import { api, tokenStorage } from '../api/client.js'
import { createPromiseCache } from './promiseCache.js'

// Keyed by current JWT to prevent cross-account reuse; bounded and short-lived.
const cached=createPromiseCache({maxEntries:150,ttlMs:90_000})
const lookup=(kind,id,loader)=>cached(`${tokenStorage.get()||''}:${kind}:${id}`,loader)
export const cachedRoom = id => lookup('room',id,()=>api.room(id))
export const cachedHotel = id => lookup('hotel',id,()=>api.hotel(id))
export const cachedRoomPhotos = id => lookup('roomPhotos',id,()=>api.roomPhotos(id))
