// verify-razorpay-payment — proves a checkout success callback is genuine.
//
// Called by the app after Razorpay Checkout reports success, BEFORE the order
// is placed. Recomputes HMAC-SHA256(order_id|payment_id) with the key secret
// and compares it to Razorpay's signature — a client can fake a success
// callback, but it cannot fake this signature without the secret.
//
// Auth: the caller's Supabase access-token JWT (Authorization header).
//
// Required function secrets:
//   RAZORPAY_KEY_SECRET

import { serve } from "https://deno.land/std@0.208.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function toHex(bytes: Uint8Array): string {
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// Constant-time compare so a wrong signature reveals nothing about the right one.
function signaturesMatch(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    if (!req.headers.get("Authorization")) return jsonResponse({ valid: false }, 401);

    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET") ?? "";
    if (!keySecret) return jsonResponse({ error: "payment verification is not configured" }, 500);

    const { order_id, payment_id, signature } = await req.json();
    if (!order_id || !payment_id || !signature) {
      return jsonResponse({ valid: false, error: "order_id, payment_id and signature are required" }, 400);
    }

    const key = await crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(keySecret),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["sign"],
    );
    const mac = new Uint8Array(
      await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${order_id}|${payment_id}`)),
    );
    const valid = signaturesMatch(toHex(mac), String(signature));

    return jsonResponse({ valid }, valid ? 200 : 402);
  } catch (e) {
    console.error("verify-razorpay-payment failed:", e);
    return jsonResponse({ valid: false }, 500);
  }
});
