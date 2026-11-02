import { useEffect, useRef } from 'react'
const FOCUSABLE = 'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])'
/** Trap keyboard focus in a portal dialog and restore focus to the opener. */
export function useModalFocus(open,onClose,allowEscape=true) {
  const ref=useRef(null)
  const closeRef=useRef(onClose)
  closeRef.current=onClose
  const allowEscapeRef=useRef(allowEscape)
  allowEscapeRef.current=allowEscape
  useEffect(()=>{
    if(!open)return
    const previous=document.activeElement
    const node=ref.current
    const focusables=()=>[...node.querySelectorAll(FOCUSABLE)].filter(el=>el.getClientRects().length>0)
    ;(focusables()[0]||node).focus()
    function keydown(event){
      if(event.key==='Escape' && allowEscapeRef.current){event.preventDefault();event.stopPropagation();closeRef.current();return}
      if(event.key!=='Tab')return
      const nodes=focusables()
      if(!nodes.length){event.preventDefault();node.focus();return}
      const first=nodes[0],last=nodes[nodes.length-1]
      if(event.shiftKey && (document.activeElement===first||!node.contains(document.activeElement))){last.focus();event.preventDefault()}
      else if(!event.shiftKey && (document.activeElement===last||!node.contains(document.activeElement))){first.focus();event.preventDefault()}
    }
    document.addEventListener('keydown',keydown,true)
    return()=>{document.removeEventListener('keydown',keydown,true);if(previous?.isConnected)previous.focus()}
  },[open])
  return ref
}
