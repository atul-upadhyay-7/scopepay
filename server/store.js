import {MongoClient} from 'mongodb';
export async function connectStore(uri, dbName) {
  const client = new MongoClient(uri, {serverSelectionTimeoutMS:5000});
  await client.connect();
  const orders=client.db(dbName).collection('sandbox_orders');
  const scopes=client.db(dbName).collection('scope_drafts');
  await scopes.createIndex({scopeId:1},{unique:true});
  await orders.createIndex({requestId:1},{unique:true});
  await orders.createIndex({orderId:1},{unique:true,sparse:true});
  return {
    async reserve(record) { try { await orders.insertOne(record); return true; } catch(e) { if(e.code===11000)return false; throw e; } },
    get:requestId=>orders.findOne({requestId}),
    find:orderId=>orders.findOne({orderId}),
    async save(requestId, fields) { await orders.updateOne({requestId},{$set:fields}); },
    saveDraft:record=>scopes.insertOne(record),
    findDraft:scopeId=>scopes.findOne({scopeId}),
    reviewDraft:(scopeId,owner)=>scopes.updateOne({scopeId,owner},{$set:{reviewedAt:new Date(),state:'REVIEWED_FOR_SANDBOX'}}),
    close:()=>client.close()
  };
}
