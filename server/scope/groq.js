/** One fixed inference endpoint/model. No tools, retries, paid fallback or client keys. */
export function createGroqProvider({apiKey,freePlanConfirmed=false,syntheticOnly=true,fetchImpl=fetch,maxRequests=50}={}){
 if(!apiKey||!freePlanConfirmed)return undefined;
 let requests=0;
 return {async generate({instructions,brief,schema,signal}){
  if(!syntheticOnly||requests>=maxRequests)throw new Error('AI request policy limit');
  requests++;
  const response=await fetchImpl('https://api.groq.com/openai/v1/chat/completions',{
   method:'POST',signal,headers:{Authorization:`Bearer ${apiKey}`,'Content-Type':'application/json'},
   body:JSON.stringify({model:'openai/gpt-oss-20b',temperature:0,max_completion_tokens:2500,
    messages:[{role:'system',content:instructions+'\nRequired JSON schema (all keys must be present, including conflicts: [] when none): '+JSON.stringify(schema)},{role:'user',content:brief}],
    response_format:{type:'json_schema',json_schema:{name:'scope_draft',strict:true,schema}}})
  });
  if(!response.ok){
   // Some provider refusals arrive as a schema-validation 400. Accept only an
   // exact refusal object for classification, never a failed draft as success.
   if(response.status===400){const error=await response.json().catch(()=>({}));
    if(error.error?.code==='json_validate_failed'&&typeof error.error?.failed_generation==='string'){
     try{const failed=JSON.parse(error.error.failed_generation);if(failed&&Object.keys(failed).length===1&&typeof failed.refusal==='string')return {refusal:true};}catch{}
    }
   }
   throw new Error('AI provider unavailable');
  }
  const data=await response.json();const choice=data.choices?.[0];
  if(choice?.message?.refusal)return {refusal:true};
  if(choice?.finish_reason!=='stop'||typeof choice?.message?.content!=='string')throw new Error('Incomplete AI output');
  return {data:JSON.parse(choice.message.content)};
 }};
}
