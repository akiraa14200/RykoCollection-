type VercelRequest = { method?: string };
type VercelResponse = {
  status: (code: number) => VercelResponse;
  setHeader: (name: string, value: string) => void;
  json: (body: unknown) => void;
};

const FEEDS = [
  { name: 'Anime News Network', category: 'Industry', url: 'https://www.animenewsnetwork.com/all/rss.xml?ann-edition=us' },
  { name: 'Crunchyroll News', category: 'Releases', url: 'https://www.crunchyroll.com/news/feed' },
  { name: 'Anime Corner', category: 'Anime', url: 'https://animecorner.me/feed/' },
  { name: 'Manga Mogura', category: 'Manga', url: 'https://mangamogura.com/feed/' },
  { name: 'The Anime Daily', category: 'Anime', url: 'https://www.theanimedaily.com/feed/' }
];

const decode = (value: string) => value
  .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
  .replace(/&amp;/gi, '&').replace(/&quot;/gi, '"').replace(/&#39;|&apos;/gi, "'")
  .replace(/&lt;/gi, '<').replace(/&gt;/gi, '>')
  .replace(/&#x([\da-f]+);/gi, (_, code) => String.fromCodePoint(parseInt(code, 16)))
  .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Number(code)));

const tagValue = (block: string, name: string) => {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = block.match(new RegExp('<(?:[\\w.-]+:)?' + escaped + '(?:\\s[^>]*)?>([\\s\\S]*?)<\\/(?:[\\w.-]+:)?' + escaped + '\\s*>', 'i'));
  return match ? decode(match[1]).trim() : '';
};

const tagAttributes = (block: string, tag: string) => {
  const escaped = tag.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const matches = [...block.matchAll(new RegExp('<(?:[\\w.-]+:)?' + escaped + '\\b[^>]*>', 'gi'))];
  return matches.map(match => {
    const attrs: Record<string, string> = {};
    for (const attr of match[0].matchAll(/([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/g)) {
      attrs[attr[1].toLowerCase()] = decode(attr[2] ?? attr[3] ?? attr[4] ?? '');
    }
    return attrs;
  });
};

const safeImage = (value: string, base: string) => {
  try {
    const url = new URL(value, base);
    return ['http:', 'https:'].includes(url.protocol) ? url.toString() : undefined;
  } catch { return undefined; }
};

const imageInMarkup = (markup: string, base: string) => {
  const candidates: string[] = [];
  for (const match of markup.matchAll(/<img\b[^>]*>/gi)) {
    const attrs: Record<string, string> = {};
    for (const attr of match[0].matchAll(/([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/g)) {
      attrs[attr[1].toLowerCase()] = decode(attr[2] ?? attr[3] ?? attr[4] ?? '');
    }
    const src = attrs.src || attrs['data-src'] || attrs['data-lazy-src'] || attrs['data-original'];
    if (src) candidates.push(src);
    if (attrs.srcset) candidates.push(attrs.srcset.split(',')[0].trim().split(/\s+/)[0]);
  }
  return candidates.map(value => safeImage(value, base)).find(Boolean);
};

const parseItem = (block: string, feed: typeof FEEDS[number]) => {
  const title = tagValue(block, 'title').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
  const link = tagValue(block, 'link') || tagAttributes(block, 'link').find(attrs => attrs.href)?.href || '';
  const description = tagValue(block, 'description') || tagValue(block, 'summary') || tagValue(block, 'encoded');
  const content = tagValue(block, 'encoded') || tagValue(block, 'content') || description;
  const pubDate = tagValue(block, 'pubDate') || tagValue(block, 'published') || tagValue(block, 'updated') || tagValue(block, 'date');
  const enclosure = tagAttributes(block, 'enclosure').find(attrs => attrs.url && (!attrs.type || attrs.type.startsWith('image/')));
  const media = [...tagAttributes(block, 'thumbnail'), ...tagAttributes(block, 'content')].find(attrs => attrs.url && (attrs.medium === 'image' || attrs.type?.startsWith('image/') || /\.(?:jpe?g|png|webp|avif)(?:$|[?#])/i.test(attrs.url)));
  const atomEnclosure = tagAttributes(block, 'link').find(attrs => attrs.rel === 'enclosure' && attrs.href && (!attrs.type || attrs.type.startsWith('image/')));
  const base = feed.url;
  const thumbnail = (media?.url && safeImage(media.url, base)) ||
    (enclosure?.url && safeImage(enclosure.url, base)) ||
    (atomEnclosure?.href && safeImage(atomEnclosure.href, base)) ||
    imageInMarkup(content, base) || imageInMarkup(description, base);
  return {
    title, link, description: description.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 360),
    pubDate, source: feed.name, category: feed.category, thumbnail
  };
};

export default async function handler(req: VercelRequest, res: VercelResponse) {
  res.setHeader('Cache-Control', 'public, s-maxage=300, stale-while-revalidate=900');
  res.setHeader('Access-Control-Allow-Origin', '*');
  if (req.method === 'OPTIONS') return res.status(204).json({});
  if (req.method !== 'GET') return res.status(405).json({ error: 'Method not allowed' });

  const results = await Promise.allSettled(FEEDS.map(async feed => {
    const response = await fetch(feed.url, {
      signal: AbortSignal.timeout(7500),
      headers: {
        Accept: 'application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.5',
        'User-Agent': 'Mozilla/5.0 (compatible; RykoNewsroom/1.0)'
      }
    });
    if (!response.ok) throw new Error(feed.name + ' returned HTTP ' + response.status);
    const xml = (await response.text()).slice(0, 2_500_000);
    const blocks = [...xml.matchAll(/<(?:item|entry)\b[^>]*>([\s\S]*?)<\/(?:item|entry)\s*>/gi)];
    return blocks.slice(0, 30).map(match => parseItem(match[1], feed)).filter(item => item.title && item.link);
  }));

  const items = results.flatMap(result => result.status === 'fulfilled' ? result.value : []);
  return res.status(200).json({
    items,
    failedSources: results.flatMap((result, index) => result.status === 'rejected' ? [FEEDS[index].name] : [])
  });
}
