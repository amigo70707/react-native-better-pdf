/** What an aborted operation rejects with: the signal's reason, or an `AbortError`. */
export function abortReason(signal: AbortSignal): unknown {
  // React Native's AbortSignal (types and polyfill) has no `reason`.
  if ('reason' in signal && signal.reason !== undefined) return signal.reason;
  const error = new Error('The operation was aborted');
  error.name = 'AbortError';
  return error;
}
