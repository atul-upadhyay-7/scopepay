const BASE = 'https://api-m.sandbox.paypal.com';
export class PayPalError extends Error {
  constructor(status, issue = 'PAYPAL_UNAVAILABLE') { super(issue); this.status = status; this.issue = issue; }
}
export function createPayPal({clientId, clientSecret, fetchImpl = fetch}) {
  let token; let expiresAt = 0;
  async function getToken() {
    if (token && Date.now() < expiresAt) return token;
    const r = await fetchImpl(`${BASE}/v1/oauth2/token`, {
      method:'POST', headers:{Authorization:`Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}`,'Content-Type':'application/x-www-form-urlencoded'},
      body:'grant_type=client_credentials', signal:AbortSignal.timeout(15000)
    });
    if (!r.ok) throw new PayPalError(502, 'PAYPAL_AUTH_FAILED');
    const data = await r.json();
    if (!data.access_token) throw new PayPalError(502);
    token=data.access_token; expiresAt=Date.now()+Math.max(0,(Number(data.expires_in)||0)-60)*1000;
    return token;
  }
  async function request(path, body, requestId) {
    const accessToken = await getToken();
    const r = await fetchImpl(`${BASE}${path}`, {method:'POST', headers:{Authorization:`Bearer ${accessToken}`,'Content-Type':'application/json','PayPal-Request-Id':requestId}, body:JSON.stringify(body),signal:AbortSignal.timeout(20000)});
    const data = await r.json().catch(()=>({}));
    if (!r.ok) throw new PayPalError(502, data.details?.[0]?.issue || 'PAYPAL_REQUEST_FAILED');
    return data;
  }
  return {
    create:({price,requestId})=>request('/v2/checkout/orders',{intent:'CAPTURE',purchase_units:[{reference_id:price.sku,amount:{currency_code:price.currency,value:price.value}}]},requestId),
    capture:({orderId,requestId})=>request(`/v2/checkout/orders/${encodeURIComponent(orderId)}/capture`,{},requestId)
  };
}
