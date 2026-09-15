// send-push — delivers a data-only FCM push for one notifications row.
//
// Triggered by: the trg_notifications_push DB trigger (see
// supabase_production_addon.sql), which POSTs {user_id, title, body, order_id}
// here after every notification insert.
//
// Flow: verify webhook secret → look up the user's device_tokens (service
// role) → mint a Google OAuth token from the FCM service account → send a
// DATA-ONLY FCM v1 message to each token. Data-only (no top-level
// "notification" block) so DugguFcmService always renders it, even when the
// app is closed or killed.
//
// Required function secrets (Dashboard → Edge Functions → send-push → Secrets,
// or `supabase secrets set`):
//   PUSH_WEBHOOK_SECRET   — must match the trigger's webhook_secret
//   FCM_SERVICE_ACCOUNT   — the whole Firebase service-account JSON
//                             (Firebase Console → Project settings →
//                              Service accounts → Generate new private key)
// SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY are provided automatically.

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-push-secret",
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

// --- Google OAuth (JWT signed with the service account, no dependencies) ---

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): Uint8Array {
  const b64 = pem.replace(/-----BEGIN [^-]+-----/g, "").replace(/-----END [^-]+-----/g, "").replace(
    /\s/g,
    "",
  );
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function googleAccessToken(serviceAccount: {
  client_email: string;
  private_key: string;
}): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64UrlEncode(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claim = base64UrlEncode(
    new TextEncoder().encode(
      JSON.stringify({
        iss: serviceAccount.client_email,
        scope: "https://www.googleapis.com/auth/firebase.messaging",
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
      }),
    ),
  );
  const unsignedToken = `${header}.${claim}`;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsignedToken)),
  );
  const assertion = `${unsignedToken}.${base64UrlEncode(signature)}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!res.ok) throw new Error(`OAuth token request failed: ${res.status} ${await res.text()}`);
  const data = await res.json();
  return data.access_token as string;
}

// --- Request handler ---

serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    // 1. Only the DB trigger (which knows the shared secret) may call this.
    const secret = Deno.env.get("PUSH_WEBHOOK_SECRET") ?? "";
    if (!secret || req.headers.get("x-push-secret") !== secret) {
      return jsonResponse({ error: "unauthorized" }, 401);
    }

    const { user_id, title, body, order_id } = await req.json();
    if (!user_id || !title) return jsonResponse({ error: "user_id and title are required" }, 400);

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const serviceAccount = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "{}");
    if (!serviceAccount.project_id || !serviceAccount.client_email || !serviceAccount.private_key) {
      return jsonResponse({ error: "FCM_SERVICE_ACCOUNT secret is missing or incomplete" }, 500);
    }

    // 2. Every device this user is signed in on.
    const tokensRes = await fetch(
      `${supabaseUrl}/rest/v1/device_tokens?user_id=eq.${user_id}&select=token`,
      { headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` } },
    );
    if (!tokensRes.ok) throw new Error(`device_tokens lookup failed: ${await tokensRes.text()}`);
    const tokens: Array<{ token: string }> = await tokensRes.json();
    if (tokens.length === 0) return jsonResponse({ ok: true, sent: 0, reason: "no devices" });

    // 3. Send data-only pushes (DugguFcmService renders them).
    const accessToken = await googleAccessToken(serviceAccount);
    let sent = 0;
    const failures: string[] = [];
    for (const { token } of tokens) {
      const fcmRes = await fetch(
        `https://fcm.googleapis.com/v1/projects/${serviceAccount.project_id}/messages:send`,
        {
          method: "POST",
          headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
          body: JSON.stringify({
            message: {
              token,
              data: {
                title: String(title),
                body: String(body ?? ""),
                order_id: String(order_id ?? ""),
                type: "ORDER",
              },
              android: { priority: "HIGH" },
            },
          }),
        },
      );
      if (fcmRes.ok) {
        sent++;
      } else {
        failures.push(`${token.slice(0, 12)}…: ${await fcmRes.text()}`);
      }
    }

    // 4. Prune dead tokens so the table doesn't rot (batch delete, best-effort).
    if (failures.length > 0) {
      try {
        const dead = tokens
          .filter((_, i) => i < failures.length && failures[i].includes("UNREGISTERED"))
          .map((t) => t.token);
        for (const d of dead) {
          await fetch(`${supabaseUrl}/rest/v1/device_tokens?token=eq.${encodeURIComponent(d)}`, {
            method: "DELETE",
            headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}` },
          });
        }
      } catch {
        // Pruning is hygiene, never worth failing the request over.
      }
    }

    return jsonResponse({ ok: true, sent, failed: failures.length });
  } catch (e) {
    console.error("send-push failed:", e);
    return jsonResponse({ error: String(e?.message ?? e) }, 500);
  }
});
