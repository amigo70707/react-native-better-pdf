import { processColor, type ColorValue } from 'react-native';

/**
 * `color` as the ARGB integer native views take, or `fallback` when it is
 * undefined or cannot be flattened to one colour (invalid strings,
 * `PlatformColor`, `DynamicColorIOS`).
 */
export function nativeColor(
  color: ColorValue | undefined,
  fallback: number,
): number {
  if (color == null) return fallback;
  const processed = processColor(color);
  return typeof processed === 'number' ? processed : fallback;
}
