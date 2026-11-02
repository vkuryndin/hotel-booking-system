/** Short-lived cache for idempotent reads. Shares in-flight work; discards rejected reads. */
export function createPromiseCache({maxEntries=150,ttlMs=90_000,now=()=>Date.now()}={}) {
  const entries=new Map()
  return (key,loader)=>{
    const hit=entries.get(key)
    if(hit && now()-hit.time < ttlMs)return hit.promise
    const promise=Promise.resolve().then(loader)
    entries.set(key,{time:now(),promise})
    promise.catch(()=>{if(entries.get(key)?.promise===promise)entries.delete(key)})
    if(entries.size>maxEntries)entries.delete(entries.keys().next().value)
    return promise
  }
}
