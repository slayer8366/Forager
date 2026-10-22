// STUB for the failing-first step (dispatch 2026-10-21-07, Phase 2). Replaced by the cutter.
export interface RangeReader {
  readonly size: number;
  read(offset: number, length: number): Promise<Uint8Array>;
}

export interface Rectangle {
  south: number;
  west: number;
  north: number;
  east: number;
}

export const DEFAULT_MARGIN_CELLS = 1;

export async function cutRd5(
  _source: RangeReader,
  _square: string,
  _rectangle: Rectangle,
  _marginCells: number = DEFAULT_MARGIN_CELLS,
): Promise<Uint8Array> {
  throw new Error("cutRd5: not implemented");
}
