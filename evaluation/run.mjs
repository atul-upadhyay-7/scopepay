// Manual, opt-in, synthetic-only evaluation. Requires an independently verified FREE account.
import 'dotenv/config';import {readFile,writeFile} from 'node:fs/promises';
import {createGroqProvider} from '../server/scope/groq.js';import {createScopeExtractor} from '../server/scope/extract.js';
if(process.env.AI_SYNTHETIC_ONLY!=='true'||process.env.GROQ_FREE_PLAN_CONFIRMED!=='true')throw new Error('Verify Free plan, ZDR and synthetic-only settings before evaluation.');
const provider=createGroqProvider({apiKey:process.env.GROQ_API_KEY,freePlanConfirmed:true,maxRequests:6});if(!provider)throw new Error('GROQ_API_KEY missing');
const extract=createScopeExtractor({provider});const cases=JSON.parse(await readFile(new URL('./briefs.json',import.meta.url),'utf8'));const results=[];
for(const c of cases){try{const data=await extract(c.brief);results.push({id:c.id,brief:c.brief,manualCheck:c.check,result:data,semanticReview:'REQUIRED'});}catch(e){results.push({id:c.id,error:e.code||'AI_UNAVAILABLE'});break;}await new Promise(r=>setTimeout(r,25000));}
await writeFile(new URL('./results.local.json',import.meta.url),JSON.stringify({at:new Date().toISOString(),model:'openai/gpt-oss-20b',cases:results},null,2));
console.log(`Recorded ${results.length} synthetic cases in evaluation/results.local.json. Inspect every result against its manual check. Schema validation alone is not a semantic pass.`);
