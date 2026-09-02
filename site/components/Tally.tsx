import type { CSSProperties } from "react";

/**
 * Vote share drawn as a row of discrete blocks rather than a continuous bar.
 *
 * It suits the subject, and it also reads better: counting filled cells is a
 * more reliable comparison between two candidates than eyeballing the length
 * of two smooth fills. Any non-zero share lights at least one cell, so a small
 * result is never rounded into looking like no result at all.
 */
export function Tally({
  share,
  palette,
  cells = 24,
  label,
}: {
  share: number;
  palette: [string, string];
  cells?: number;
  label: string;
}) {
  const filled = share > 0 ? Math.max(1, Math.min(cells, Math.round(share * cells))) : 0;
  const [face, dark] = palette;

  const litStyle = {
    "--cell": face,
    "--cell-lit": `color-mix(in srgb, ${face} 72%, #ffffff)`,
    "--cell-dark": dark,
  } as CSSProperties;

  return (
    <div
      className="tally"
      style={{ "--cells": cells } as CSSProperties}
      role="img"
      aria-label={label}
    >
      {Array.from({ length: cells }, (_, index) => (
        <span
          key={index}
          className={index < filled ? "tally__cell tally__cell--on" : "tally__cell"}
          style={index < filled ? litStyle : undefined}
        />
      ))}
    </div>
  );
}
