import './news-page.css';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Clock3, ExternalLink, Image as ImageIcon, Newspaper, RefreshCw, Search, Tag } from 'lucide-react';

type NewsItem = {
  title: string;
  link: string;
  description: string;
  pubDate: string;
  author?: string;
  source: string;
  category: string;
  thumbnail?: string;
};
type FeedItem = {
  title?: string;
  link?: string;
  description?: string;
  content?: string;
  pubDate?: string;
  author?: string;
  thumbnail?: string;
  enclosure?: string | { link?: string; url?: string };
};
type FeedResponse = { status?: string; items?: FeedItem[] };

const FEEDS = [
  { name: 'Anime News Network', category: 'Industry', url: 'https://www.animenewsnetwork.com/all/rss.xml?ann-edition=us' },
  { name: 'Crunchyroll News', category: 'Releases', url: 'https://www.crunchyroll.com/news/feed' },
  { name: 'Anime Corner', category: 'Anime', url: 'https://animecorner.me/feed/' },
  { name: 'Manga Mogura', category: 'Manga', url: 'https://mangamogura.com/feed/' },
  { name: 'The Anime Daily', category: 'Anime', url: 'https://www.theanimedaily.com/feed/' }
];

const cleanText = (value = '') => value.replace(/<[^>]*>/g, ' ').replace(/&nbsp;|&#160;/g, ' ').replace(/&amp;/g, '&').replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'").replace(/\s+/g, ' ').trim();
const isRecent = (date: string) => {
  const time = Date.parse(date);
  return Number.isFinite(time) && time <= Date.now() + 86400000 && time >= Date.now() - 1000 * 60 * 60 * 24 * 45;
};

const safeImageUrl = (value?: string | null, base?: string) => {
  if (!value) return undefined;
  try {
    const url = new URL(value.replace(/&amp;/g, '&'), base);
    if (!['http:', 'https:'].includes(url.protocol)) return undefined;
    if (/\.(svg|gif)(?:$|\?)/i.test(url.pathname)) return undefined;
    return url.toString();
  } catch {
    return undefined;
  }
};

const imageFromMarkup = (markup = '', base?: string) => {
  const match = markup.match(/<img\b[^>]*(?:src|data-src|data-original)\s*=\s*["']([^"']+)["'][^>]*>/i);
  return safeImageUrl(match?.[1], base);
};

const imageFromFeedItem = (item: FeedItem) => {
  const enclosure = typeof item.enclosure === 'string' ? item.enclosure : item.enclosure?.link || item.enclosure?.url;
  return safeImageUrl(item.thumbnail) || safeImageUrl(enclosure) ||
    imageFromMarkup(item.content || '') || imageFromMarkup(item.description || '');
};

export function NewsPage() {
  const [items, setItems] = useState<NewsItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');
  const [category, setCategory] = useState('All');
  const [query, setQuery] = useState('');
  const requestId = useRef(0);
  const categories = ['All', 'Anime', 'Manga', 'Releases', 'Industry', 'Events'];

  const loadNews = useCallback(async (manual = false) => {
    const currentRequest = ++requestId.current;
    if (manual) setRefreshing(true); else setLoading(true);
    setError('');

    const results = await Promise.allSettled(FEEDS.map(async feed => {
      const endpoint = 'https://api.rss2json.com/v1/api.json?rss_url=' + encodeURIComponent(feed.url);
      const response = await fetch(endpoint, { headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error(feed.name + ' feed unavailable');
      const payload = await response.json() as FeedResponse;
      if (payload.status !== 'ok' || !Array.isArray(payload.items)) throw new Error(feed.name + ' feed could not be read');
      return payload.items.map(item => ({
        title: cleanText(item.title || 'Untitled story'),
        link: item.link || '',
        description: cleanText(item.description || item.content || '').slice(0, 360),
        pubDate: item.pubDate || '',
        author: item.author || '',
        source: feed.name,
        category: feed.category,
        thumbnail: imageFromFeedItem(item)
      })).filter(item => item.link && item.title && isRecent(item.pubDate));
    }));

    if (currentRequest !== requestId.current) return;
    const merged = results.flatMap(result => result.status === 'fulfilled' ? result.value : []);
    const unique = [...new Map(merged.map(item => [item.link, item])).values()]
      .sort((a, b) => Date.parse(b.pubDate) - Date.parse(a.pubDate)).slice(0, 100);
    setItems(unique);
    if (!unique.length) setError('News feeds could not be reached right now. Please try again shortly.');
    else if (results.every(result => result.status === 'rejected')) setError('Some feeds are temporarily unavailable; showing any stories that could be loaded.');
    setLoading(false);
    setRefreshing(false);

    // RSS thumbnails are preferred. Only articles still missing an image are sent
    // to the same-origin Vercel metadata endpoint, with a small concurrency limit.
    const missingImages = unique.filter(item => !item.thumbnail).slice(0, 48);
    let nextIndex = 0;
    await Promise.all(Array.from({ length: 4 }, async () => {
      while (nextIndex < missingImages.length) {
        const item = missingImages[nextIndex++];
        try {
          const response = await fetch('/api/news-image?url=' + encodeURIComponent(item.link), {
            headers: { Accept: 'application/json' }
          });
          if (!response.ok) continue;
          const payload = await response.json() as { image?: string | null };
          const image = safeImageUrl(payload.image);
          if (image && currentRequest === requestId.current) {
            setItems(current => current.map(existing =>
              existing.link === item.link ? { ...existing, thumbnail: image } : existing
            ));
          }
        } catch {
          // A publisher may block metadata requests; keep the article and placeholder.
        }
      }
    }));
  }, []);

  useEffect(() => {
    void loadNews();
    return () => { requestId.current += 1; };
  }, [loadNews]);

  const filtered = useMemo(() => items.filter(item => {
    const matchesCategory = category === 'All' || (category === 'Events' ? /event|convention|expo|festival|animejapan|comic.?con/i.test(item.title + ' ' + item.description) : category === 'Releases' ? /release|premiere|airing|debut|date|season|episode|stream/i.test(item.title + ' ' + item.description) : category === 'Industry' ? /industry|studio|production|box office|award|business|staff|director/i.test(item.title + ' ' + item.description) : category === 'Manga' ? /manga|manhwa|manhua|webtoon|comic/i.test(item.title + ' ' + item.description + ' ' + item.source) : /anime/i.test(item.title + ' ' + item.description + ' ' + item.source));
    const matchesQuery = !query.trim() || (item.title + ' ' + item.description + ' ' + item.source).toLowerCase().includes(query.trim().toLowerCase());
    return matchesCategory && matchesQuery;
  }), [items, category, query]);

  return <main className="content-page ryko-news-page">
    <header className="news-hero">
      <span className="eyebrow"><Newspaper size={14}/> THE ANIME WORLD, IN ONE PLACE</span>
      <h1>Ryko <em>Newsroom.</em></h1>
      <p>Anime and manga headlines, release updates, studio announcements, events and industry stories from around the world.</p>
      <div className="news-toolbar">
        <label className="news-search"><Search size={16}/><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Search anime news, releases, events…"/></label>
        <button className="secondary-btn" type="button" onClick={() => void loadNews(true)} disabled={refreshing}><RefreshCw size={15} className={refreshing ? 'news-spinning' : ''}/> Refresh</button>
      </div>
    </header>
    <div className="news-categories" role="tablist" aria-label="News categories">{categories.map(value => <button key={value} type="button" role="tab" aria-selected={category === value} className={category === value ? 'active' : ''} onClick={() => setCategory(value)}>{value}</button>)}</div>
    <div className="news-feed-heading"><div><h2>Latest updates</h2><p>Collected from anime and manga publishers and news outlets</p></div><span>{filtered.length} stories</span></div>
    {loading ? <div className="news-state">Collecting the latest headlines…</div> : error && !items.length ? <div className="news-state">{error}<button className="secondary-btn" onClick={() => void loadNews(true)}>Try again</button></div> : filtered.length ? <section className="news-grid">{filtered.map(item => <article className="news-card" key={item.link}>
      <a href={item.link} target="_blank" rel="noreferrer" className="news-image" aria-label={'Open article: ' + item.title}>
        <div className="news-image-placeholder"><ImageIcon size={25}/><span>Image unavailable</span></div>
        {item.thumbnail && <img src={item.thumbnail} alt="" loading="lazy" decoding="async" referrerPolicy="no-referrer" onError={event => event.currentTarget.remove()}/>}
      </a>
      <div className="news-card-content"><div className="news-meta"><span>{item.source}</span><span><Clock3 size={12}/>{new Date(item.pubDate).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}</span></div><h3><a href={item.link} target="_blank" rel="noreferrer">{item.title}</a></h3>{item.description && <p>{item.description}</p>}<a className="news-read-link" href={item.link} target="_blank" rel="noreferrer">Read full story <ExternalLink size={13}/></a></div>
    </article>)}</section> : <div className="news-state">No stories match this filter. Try another category or search term.</div>}
    <footer className="news-sources"><Tag size={14}/> Sources: {FEEDS.map(feed => feed.name).join(' · ')}. Headlines link to their original publishers.</footer>
  </main>;
}
