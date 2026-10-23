// Failing-first stub (dispatch 2026-10-23-01): the route-data handler is not built yet. The next commit replaces it.
export const APP_KEY_HEADER = "X-Forager-App-Key";
export async function routingResponse(_request: Request, _env: unknown, _cache: unknown): Promise<Response> {
  return new Response("not built", { status: 404 });
}
