/** Pure helper for numeric pagination, using zero-based internal page indexes. */
export function getPageWindow(page,totalPages,windowSize=5){
  const max=Math.max(1,Math.floor(Number(totalPages)||1))
  const current=Math.min(Math.max(0,Math.floor(Number(page)||0)),max-1)
  const count=Math.min(Math.max(1,windowSize),max)
  const start=Math.max(0,Math.min(max-count,current-Math.floor(count/2)))
  return {current,max,start,pages:Array.from({length:count},(_,i)=>start+i)}
}
