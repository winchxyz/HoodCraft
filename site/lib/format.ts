/** Client-safe formatting. Deliberately has no "server-only" import. */

/**
 * Renders a raw token amount without ever touching a float.
 *
 * Balances arrive as decimal strings because an 18-decimal balance does not
 * survive Number(), so the whole and fractional parts are split with BigInt
 * arithmetic and only ever concatenated as text.
 */
export function formatWeight(raw: string, decimals: number): string {
  let value: bigint;
  try {
    value = BigInt(raw);
  } catch {
    return "0";
  }
  if (decimals <= 0) return value.toLocaleString("en-US");

  const base = 10n ** BigInt(decimals);
  const whole = value / base;
  const remainder = value % base;
  const wholeText = whole.toLocaleString("en-US");
  if (remainder === 0n) return wholeText;

  // Two fractional digits is plenty for a tally; trailing zeros are dropped.
  const fraction = remainder.toString().padStart(decimals, "0").slice(0, 2).replace(/0+$/, "");
  return fraction ? `${wholeText}.${fraction}` : wholeText;
}

export function shortAddress(address: string): string {
  if (address.length < 12) return address;
  return `${address.slice(0, 6)}…${address.slice(-4)}`;
}

export function formatShare(share: number): string {
  if (share <= 0) return "0%";
  if (share < 0.001) return "<0.1%";
  return `${(share * 100).toFixed(1)}%`;
}
