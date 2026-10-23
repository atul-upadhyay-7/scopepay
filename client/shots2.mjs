import { chromium } from 'playwright';
const base = 'http://localhost:4517';
const H = {'Content-Type':'application/json'};
const draftFixture = {
  scopeId: '9908358d-3f92-4fd1-a476-2df6fd720982',
  summary: 'Bakery website redesign - FIXTURE DATA for UI screenshot, not a live AI result',
  state: 'REVIEWED_FOR_SANDBOX',
  reviewedAt: '2026-10-23T09:11:19Z',
  deliverables: [
    {description: 'Five-page responsive website (home, menu, about, order, contact)', evidence: 'we need a 5 page site with online ordering for our bakery'},
    {description: 'Online ordering flow with pickup time selection', evidence: 'customers should pick a pickup time when they order'},
    {description: 'Photo gallery of products supplied by the client', evidence: 'we will send you our product photos'}
  ],
  questions: ['Which payment provider should the ordering flow use?', 'Is hosting included or billed separately?'],
  conflicts: [{description: 'Timeline conflict: brief asks for launch in 2 weeks but also requests a full custom design.', evidence: ['launch in 2 weeks', 'fully custom design, no templates']}]
};
// Clearly labelled mock of the PayPal web-sdk v6 core script - no real sandbox call is ever made.
const MOCK_SDK = `window.paypal={createInstance:async()=>({findEligibleMethods:async()=>({isEligible:()=>true}),createPayPalOneTimePaymentSession:async(h)=>({start:async()=>{setTimeout(()=>h.onCancel(),400);}})})};`;
async function driveToPayment(page, {withSdk}) {
  await page.route('**/api/**', r => r.fulfill({status:200, headers:H, body:'{}'}));
  await page.route('**/api/scopes/*/review', r => r.fulfill({status:200, headers:H, body:JSON.stringify({scopeId:draftFixture.scopeId})}));
  await page.route('**/api/scopes/extract', r => r.fulfill({status:200, headers:H, body:JSON.stringify(draftFixture)}));
  await page.route('**/api/config', r => r.fulfill({status:200, headers:H, body: withSdk ? JSON.stringify({clientId:'mock-labelled-fixture', sku:'sandbox-demo'}) : '{}'}));
  if (withSdk) {
    await page.route('**/web-sdk/v6/core**', r => r.fulfill({status:200, headers:{'Content-Type':'application/javascript'}, body:MOCK_SDK}));
    await page.route('**/api/orders', r => r.fulfill({status:200, headers:H, body:JSON.stringify({orderId:'MOCK-ORDER-LABELLED', status:'CREATED'})}));
  }
  await page.goto(base + '/brief', {waitUntil:'networkidle'});
  await page.locator('#brief').fill('We need a 5 page site with online ordering for our bakery, customers should pick a pickup time.');
  await page.getByRole('button', {name:'Extract a draft'}).click();
  await page.getByRole('checkbox').waitFor();
  await page.getByRole('checkbox').click();
  await page.getByRole('button', {name:'Save my review'}).click();
}
const browser = await chromium.launch();
const overflow = p => p.evaluate(() => ({scrollWidth: document.documentElement.scrollWidth, innerWidth: window.innerWidth, ok: document.documentElement.scrollWidth <= window.innerWidth}));

// M1: Receipts dialog on mobile, top + scrolled to bottom
{
  const ctx = await browser.newContext({viewport:{width:390,height:844}, deviceScaleFactor:2, isMobile:true, hasTouch:true});
  const page = await ctx.newPage();
  await page.route('**/api/**', r => r.fulfill({status:200, headers:H, body:'{}'}));
  await page.route('**/api/config', r => r.fulfill({status:200, headers:H, body:'{}'}));
  await page.route('**/api/receipts', r => r.fulfill({status:200, headers:H, body:JSON.stringify({drafts:[draftFixture], orders:[]})}));
  await page.goto(base + '/receipts', {waitUntil:'networkidle'});
  await page.locator('.ag-row').first().click();
  await page.getByText('Deliverables, with source evidence').waitFor();
  console.log('M1 overflow top:', JSON.stringify(await overflow(page)));
  await page.screenshot({path:'/downloads/receipts-dialog-mobile-top.png'});
  await page.locator('[role="dialog"]').evaluate(el => { el.scrollTop = el.scrollHeight; });
  await page.waitForTimeout(400);
  console.log('M1 overflow bottom:', JSON.stringify(await overflow(page)));
  console.log('M1 dialog scrolled to:', await page.locator('[role="dialog"]').evaluate(el => el.scrollTop + '/' + el.scrollHeight));
  await page.screenshot({path:'/downloads/receipts-dialog-mobile-bottom.png'});
  await ctx.close();
}

// M2: Payment setup-issue on mobile
{
  const ctx = await browser.newContext({viewport:{width:390,height:844}, deviceScaleFactor:2, isMobile:true, hasTouch:true});
  const page = await ctx.newPage();
  await driveToPayment(page, {withSdk:false});
  await page.getByRole('button', {name:'Sandbox setup needed'}).waitFor();
  await page.waitForTimeout(2000);
  console.log('M2 overflow:', JSON.stringify(await overflow(page)));
  await page.screenshot({path:'/downloads/payment-setup-mobile.png', fullPage:true});
  await ctx.close();
}

// D3: checkoutClosed via labelled mock SDK (desktop)
{
  const ctx = await browser.newContext({viewport:{width:1280,height:900}, deviceScaleFactor:2});
  const page = await ctx.newPage();
  await driveToPayment(page, {withSdk:true});
  const payBtn = page.getByRole('button', {name:'Pay with PayPal (sandbox)'});
  await payBtn.waitFor();
  await payBtn.click();
  await page.getByRole('button', {name:'Checkout closed - reload to start again'}).waitFor();
  await page.getByText('Checkout cancelled', {exact:false}).waitFor();
  await page.waitForTimeout(1500);
  await page.screenshot({path:'/downloads/payment-checkout-closed.png'});
  await ctx.close();
}
await browser.close();
console.log('SHOTS2 DONE');
