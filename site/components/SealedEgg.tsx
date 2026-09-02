/**
 * A pixel egg drawn on a 16x16 grid, in the candidate's own colours.
 *
 * Drawn rather than photographed because a sealed slot has no mascot yet, and
 * borrowing the Robin's egg texture would imply it did.
 *
 * Shading follows pixel-art convention rather than a gradient: a small
 * specular patch up and to the left, a one-cell rim of shadow around the
 * lower-right, flat base colour everywhere between. A smooth ramp would read
 * as a 3D render dropped into a pixel site.
 */

// Pointed at the top, heaviest below the middle — an egg, not a capsule.
const MASK = [
  "................",
  ".......##.......",
  "......####......",
  ".....######.....",
  ".....######.....",
  "....########....",
  "....########....",
  "...##########...",
  "...##########...",
  "..############..",
  "..############..",
  "..############..",
  "...##########...",
  "....########....",
  ".....######.....",
  "................",
];

const filled = (row: number, col: number): boolean =>
  row >= 0 && row < MASK.length && col >= 0 && col < 16 && MASK[row][col] === "#";

/** The lower-right rim: a filled cell whose down-right neighbour is outside the shape. */
const isRim = (row: number, col: number): boolean =>
  !filled(row + 1, col + 1) && (row >= 8 || col >= 9);

/**
 * A small specular patch, up and to the left of centre. Deliberately only two
 * cells across: the egg is six cells wide up there, and anything larger stops
 * reading as a highlight and starts reading as a hood.
 */
const isSpecular = (row: number, col: number): boolean =>
  row >= 4 && row <= 5 && col >= 5 && col <= 6;

export function SealedEgg({ palette, size = 128 }: { palette: [string, string]; size?: number }) {
  const [face, dark] = palette;
  const light = `color-mix(in srgb, ${face} 70%, #ffffff)`;

  const cells: React.ReactElement[] = [];
  for (let row = 0; row < MASK.length; row++) {
    for (let col = 0; col < 16; col++) {
      if (!filled(row, col)) continue;
      const fill = isRim(row, col) ? dark : isSpecular(row, col) ? light : face;
      cells.push(<rect key={`${row}-${col}`} x={col} y={row} width={1} height={1} fill={fill} />);
    }
  }

  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 16 16"
      shapeRendering="crispEdges"
      aria-hidden
      focusable="false"
    >
      {cells}
    </svg>
  );
}
