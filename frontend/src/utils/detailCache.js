import { api } from '../api/client.js'

// The central API client now owns its bounded GET cache and invalidation after writes.
// Keep these helpers so existing booking-detail callers share that cache without
// storing the JWT in cache keys or maintaining a second stale layer.
export const cachedRoom = id => api.room(id)
export const cachedHotel = id => api.hotel(id)
export const cachedRoomPhotos = id => api.roomPhotos(id)
