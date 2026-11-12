type CanvasBounds = { key: string; blockType: string; x: number; y: number; w: number; h: number }

/** Open nearby prototypes first; distant pages keep their placeholder until visited. */
export function visiblePrototypeBlocks<T extends CanvasBounds>(
  blocks: T[], opened: ReadonlySet<string>, view: { x: number; y: number; k: number },
  width: number, height: number, margin = 240,
): T[] {
  const scale = view.k > 0 ? view.k : 1
  const minX = (-view.x - margin) / scale, maxX = (width - view.x + margin) / scale
  const minY = (-view.y - margin) / scale, maxY = (height - view.y + margin) / scale
  const centerX = (width / 2 - view.x) / scale, centerY = (height / 2 - view.y) / scale
  return blocks.filter(b => b.blockType === 'prototype' && !opened.has(b.key)
    && b.x + b.w >= minX && b.x <= maxX && b.y + b.h >= minY && b.y <= maxY)
    .sort((a, b) => Math.hypot(a.x + a.w / 2 - centerX, a.y + a.h / 2 - centerY)
      - Math.hypot(b.x + b.w / 2 - centerX, b.y + b.h / 2 - centerY))
}
