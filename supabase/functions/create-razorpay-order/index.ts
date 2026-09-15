// create-razorpay-order — mints a Razorpay order for one checkout.
//
// Called by the app (RazorpayOrderRepository) when the customer chooses online
// payment. The KEY SECRET never leaves this function — the app only ever sees
// the public key id and the resulting order_id, which it hands to the
// Razorpay Checkout SDK.
//
// Auth: the caller's Supabase access-token JWT (Authorization header) —
// service-role key is NOT needed since nothing here touches the database.
//
// Required function secrets:
//   RAZORPAY_KEY_ID       (public, also baked into the app via local.properties)
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

serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    if (!req.headers.get("Authorization")) return jsonResponse({ error: "sign in required" }, 401);

    const keyId = Deno.env.get("RAZORPAY_KEY_ID") ?? "";
    const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET") ?? "";
    if (!keyId || !keySecret) {
      return jsonResponse({ error: "Razorpay secrets are not configured on this function" }, 500);
    }

    const { amount_paise, receipt } = await req.json();
    const amount = Number(amount_paise);
    if (!Number.isInteger(amount) || amount < 100 || amount > 10000000) {
      // ₹1 floor (below that Razorpay rejects it anyway), ₹1,00,000 ceiling.
      return jsonResponse({ error: "amount_paise must be an integer between 100 and 10000000" }, 400);
    }

    const res = await fetch("https://api.razorpay.com/v1/orders", {
      method: "POST",
      headers: {
        Authorization: `Basic ${btoa(`${keyId}:${keySecret}`)}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        amount,
        currency: "INR",
        receipt: String(receipt ?? `duggu_${Date.now()}`).slice(0, 40),
      }),
    });
    const data = await res.json();
    if (!res.ok) {
      console.error("Razorpay order failed:", JSON.stringify(data));
      return jsonResponse({ error: "could not start the payment — try again" }, 502);
    }

    return jsonResponse({ order_id: data.id, amount: data.amount, key_id: keyId });
  } catch (e) {
    console.error("create-razorpay-order failed:", e);
    return jsonResponse({ error: String(e?.message ?? e) }, 500);
  }
});
