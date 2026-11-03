/**
 * A genuinely empty catalogue can be declared only after a successful response.
 * An empty array before the request completes is not evidence that no hotels exist.
 */
export function homeHotelsDisplayState({ authLoading, authenticated, loading, error, count }) {
  if (authLoading) return 'loading'
  if (!authenticated) return 'sign-in'
  if (loading && count === 0) return 'loading'
  if (error && count === 0) return 'error'
  if (!loading && !error && count === 0) return 'empty'
  return 'cards'
}
