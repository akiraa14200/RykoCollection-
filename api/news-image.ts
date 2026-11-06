type VercelRequest = { method?: string; query?: Record<string, string | string[] | undefined> };
type VercelResponse = {
  status: (code: number) => VercelResponse;
  setHeader: (name: string, value: string) => void;
  json: (body: unknown) => void;
};

const blockedHost = (hostname: string) => {
  const host = hostname.toLowerCase().replace(/^\[|\]$/g, '');
  return host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.local') ||
    host.endsWith('.internal') || host === '::1' || host === '0.0.0.0' ||
    /^127\./.test(host) || /^10\./.test(host) || /^192\.168\./.test(host) ||
    /^169\.254\./.test(host) || /^172\.(1[6-9]|2\d|3[01])\./.test(host);
};

const safeHttpUrl = (value: string, base?: string) => {
  try {
    const url = new URL(value, base);
    if (!['http:', 'https:'].includes(url.protocol) || blockedHost(url.hostname)) return null;
    url.username = '';
    url.password = '';
    return url;
  } catch {
    return null;
  }
};

const decodeEntities = (value: string) => value
  .replace(/&amp;/gi, '&').replace(/&quot;/gi, '"').replace(/&#39;|&apos;/gi, "'")
  .replace(/&lt;/gi, '<').replace(/&gt;/gi, '>')
  .replace(/&#x([\da-f]+);/gi, (_, code) => String.fromCodePoint(parseInt(code, 16)))
  .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Number(code)));

const attributes = (tag: string) => {
  const result: Record<string, string> = {};
  for (const match of tag.matchAll(/([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/g)) {
    result[match[1].toLowerCase()] = decodeEntities(match[2] ?? match[3] ?? match[4] ?? '');
  }
  return result;
};

const jsonLdImages = (value: unknown): string[] => {
  if (!value || typeof value !== 'object') return [];
  if (Array.isArray(value)) return value.flatMap(jsonLdImages);
  const record = value as Record<string, unknown>;
  const image = record.image ?? record.thumbnailUrl;
  const direct = typeof image === 'string' ? [image] :
    Array.isArray(image) ? image.flatMap(entry => typeof entry === 'string' ? [entry] :
      entry && typeof entry === 'object' ? [String((entry as Record<string, unknown>).url ?? (entry as Record<string, unknown>).contentUrl ?? '')] : []) :
    image && typeof image === 'object' ? [String((image as Record<string, unknown>).url ?? (image as Record<string, unknown>).contentUrl ?? '')] : [];
  return [...direct, ...Object.values(record).flatMap(entry =>
    entry && typeof entry === 'object' ? jsonLdImages(entry) : [])].filter(Boolean);
};

const extractImage = (html: string, pageUrl: string) => {
  const candidates: string[] = [];
  for (const match of html.matchAll(/<(meta|link)\b[^>]*>/gi)) {
    const attrs = attributes(match[0]);
    const key = (attrs.property ?? attrs.name ?? attrs.rel ?? '').toLowerCase();
    if (['og:image', 'og:image:url', 'twitter:image', 'twitter:image:src', 'image_src'].includes(key)) {
      if (attrs.content || attrs.href) candidates.push(attrs.content ?? attrs.href);
    }
  }
  for (const match of html.matchAll(/<script\b[^>]*type\s*=\s*["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi)) {
    try { candidates.push(...jsonLdImages(JSON.parse(match[1]))); } catch { /* Ignore malformed publisher JSON-LD. */ }
  }
  for (const match of html.matchAll(/<img\\b[^>]*>/gi)) {
    const attrs = attributes(match[0]);
    const src = attrs.src ?? attrs['data-src'] ?? attrs['data-lazy-src'] ?? attrs['data-original'];
    if (src && !/logo|avatar|icon|sprite|tracking|pixel/i.test(src)) candidates.push(src);
    if (attrs.srcset) candidates.push(attrs.srcset.split(',')[0].trim().split(/\\s+/)[0]);
  }
  for (const candidate of candidates) {
    const image = safeHttpUrl(candidate, pageUrl);
    if (image && !/\\.svg(?:$|\\?)/i.test(image.pathname)) return image.toString();
  }
  return null;
};

export default async function handler(req: VercelRequest, res: VercelResponse) {
  res.setHeader('Cache-Control', 'public, s-maxage=86400, stale-while-revalidate=604800');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  if (req.method === 'OPTIONS') return res.status(204).json({});
  if (req.method !== 'GET') return res.status(405).json({ error: 'Method not allowed' });

  const raw = req.query?.url;
  const requestedUrl = Array.isArray(raw) ? raw[0] : raw;
  if (!requestedUrl || requestedUrl.length > 2048) return res.status(400).json({ error: 'A valid article URL is required' });
  let current = safeHttpUrl(requestedUrl);
  if (!current) return res.status(400).json({ error: 'Only public HTTP(S) article URLs are supported' });

  try {
    let response: Response | null = null;
    for (let redirects = 0; redirects <= 4; redirects++) {
      response = await fetch(current, {
        redirect: 'manual',
        signal: AbortSignal.timeout(7000),
        headers: {
          Accept: 'text/html,application/xhtml+xml;q=0.9,*/*;q=0.5',
          'User-Agent': 'Mozilla/5.0 (compatible; RykoNewsImageBot/1.0; +https://rykocollection.vercel.app)'
        }
      });
      if (![301, 302, 303, 307, 308].includes(response.status)) break;
      const location = response.headers.get('location');
      if (!location || redirects === 4) break;
      const next = safeHttpUrl(location, current.toString());
      if (!next) return res.status(200).json({ image: null });
      current = next;
    }
    if (!response?.ok || !/text\/html|application\/xhtml\+xml/i.test(response.headers.get('content-type') ?? '')) {
      return res.status(200).json({ image: null });
    }
    const html = (await response.text()).slice(0, 1_500_000);
    return res.status(200).json({ image: extractImage(html, current.toString()) });
  } catch {
    return res.status(200).json({ image: null });
  }
}
