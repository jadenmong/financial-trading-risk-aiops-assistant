import type { Request, RequestHandler } from 'express';

export function validateHostAndOrigin(allowedHosts: string[], allowedOrigins: string[]): RequestHandler {
  const hostSet = new Set(allowedHosts.map((item) => item.toLowerCase()));
  const originSet = new Set(allowedOrigins.map((item) => new URL(item).origin.toLowerCase()));
  return (request, response, next) => {
    const hostname = hostnameOf(request);
    if (!hostname || !hostSet.has(hostname.toLowerCase())) {
      response.status(403).json({ error: 'invalid_host' });
      return;
    }
    const origin = request.get('origin');
    if (origin) {
      try {
        if (!originSet.has(new URL(origin).origin.toLowerCase())) {
          response.status(403).json({ error: 'invalid_origin' });
          return;
        }
      } catch {
        response.status(403).json({ error: 'invalid_origin' });
        return;
      }
    }
    next();
  };
}

function hostnameOf(request: Request): string | undefined {
  const host = request.get('host');
  if (!host) return undefined;
  try { return new URL(`http://${host}`).hostname; } catch { return undefined; }
}
