import { useEffect, useState, type FormEvent } from 'react';
import type { User } from '@supabase/supabase-js';
import { Heart, MessageCircle, Send, Trophy } from 'lucide-react';
import { Link } from 'react-router-dom';
import { supabase } from '../lib/supabase';
import { getAnime, imageOf, titleOf, type Anime } from '../lib/catalogue';

const numericAniListId = (value: string) => {
  const match = String(value).match(/(\d+)$/);
  return match ? Number(match[1]) : NaN;
};

type CommentRow = {
  id: string; anilist_id: number; user_id: string; parent_comment_id: string | null;
  body: string; spoiler: boolean; moderation_status: string; created_at: string;
  author?: { username?: string; display_name?: string };
};

export function EntryLikeButton({ animeId, user }: { animeId: string; user: User | null }) {
  const id = numericAniListId(animeId);
  const [liked, setLiked] = useState(false);
  const [count, setCount] = useState(0);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!Number.isSafeInteger(id) || id <= 0) return;
    let active = true;
    void (async () => {
      const [{ data: total }, mine] = await Promise.all([
        supabase.rpc('get_ryko_entry_like_count', { entry_id: id }),
        user ? supabase.from('anime_entry_likes').select('id').eq('anilist_id', id).eq('user_id', user.id).maybeSingle() : Promise.resolve({ data: null, error: null })
      ]);
      if (active) { setCount(Number(total || 0)); setLiked(!!mine.data); }
    })();
    return () => { active = false; };
  }, [id, user?.id]);
  const toggle = async () => {
    if (!user || busy || !Number.isSafeInteger(id) || id <= 0) return;
    setBusy(true);
    if (liked) {
      const { error } = await supabase.from('anime_entry_likes').delete().eq('anilist_id', id).eq('user_id', user.id);
      if (!error) { setLiked(false); setCount(value => Math.max(0, value - 1)); }
    } else {
      const { error } = await supabase.from('anime_entry_likes').insert({ anilist_id: id, user_id: user.id });
      if (!error) { setLiked(true); setCount(value => value + 1); }
    }
    setBusy(false);
  };
  return <button type="button" className={liked ? 'secondary-btn entry-like-button liked' : 'secondary-btn entry-like-button'} onClick={() => void toggle()} disabled={!user || busy || !Number.isSafeInteger(id) || id <= 0} title={!user ? 'Sign in to like this entry' : liked ? 'Unlike this entry' : 'Like this entry'}><Heart size={16} fill={liked ? 'currentColor' : 'none'}/>{liked ? 'Liked' : 'Like'} <span>{count.toLocaleString()}</span></button>;
}

export function EntryComments({ animeId, animeTitle, user }: { animeId: string; animeTitle: string; user: User | null }) {
  const id = numericAniListId(animeId);
  const [rows, setRows] = useState<CommentRow[]>([]);
  const [draft, setDraft] = useState('');
  const [spoiler, setSpoiler] = useState(false);
  const [revealed, setRevealed] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const load = async () => {
    if (!Number.isSafeInteger(id) || id <= 0) { setRows([]); setLoading(false); return; }
    setLoading(true);
    const { data, error: queryError } = await supabase.from('anime_entry_comments')
      .select('id,anilist_id,user_id,parent_comment_id,body,spoiler,moderation_status,created_at')
      .eq('anilist_id', id).eq('moderation_status', 'approved')
      .order('created_at', { ascending: false }).limit(100);
    if (queryError) { setError(queryError.message); setRows([]); setLoading(false); return; }
    const comments = (data || []) as CommentRow[];
    const ids = [...new Set(comments.map(row => row.user_id))];
    if (ids.length) {
      const { data: profiles } = await supabase.from('anime_entry_profiles').select('id,username,display_name').in('id', ids);
      const byId = new Map((profiles || []).map(profile => [profile.id, profile]));
      comments.forEach(row => { row.author = byId.get(row.user_id); });
    }
    setRows(comments); setLoading(false);
  };
  useEffect(() => { void load(); }, [id]);
  const post = async (event: FormEvent) => {
    event.preventDefault();
    if (!user) { setError('Sign in to comment.'); return; }
    const body = draft.trim();
    if (!body || body.length > 3000 || !Number.isSafeInteger(id) || id <= 0) return;
    setBusy(true); setError('');
    const { error: insertError } = await supabase.from('anime_entry_comments').insert({
      anilist_id: id, user_id: user.id, body, spoiler, moderation_status: 'pending'
    });
    if (insertError) setError(insertError.message);
    else { setDraft(''); setSpoiler(false); setError('Your comment was submitted for safety review and will appear after approval.'); }
    setBusy(false);
  };
  const visibleCount = rows.filter(row => !row.parent_comment_id).length;
  return <section className="anime-comments entry-comments">
    <div className="section-head"><div><h2><MessageCircle size={18}/> Comments</h2><p>Discuss {animeTitle} respectfully. New comments are reviewed before appearing.</p></div><span>{visibleCount}</span></div>
    {user ? <form className="anime-comment-compose" onSubmit={post}>
      <textarea value={draft} onChange={event => setDraft(event.target.value)} placeholder="Share your thoughts about this entry…" maxLength={3000} required/>
      <div className="entry-comment-actions"><label><input type="checkbox" checked={spoiler} onChange={event => setSpoiler(event.target.checked)}/> Contains spoilers</label><small>{draft.length}/3000</small><button className="primary-btn" disabled={busy || !draft.trim()}><Send size={15}/>{busy ? 'Submitting…' : 'Post comment'}</button></div>
    </form> : <div className="entry-empty"><MessageCircle size={20}/> Sign in to join the discussion.</div>}
    {error && <p className="anime-comment-error" role="status">{error}</p>}
    {loading ? <p className="fine-print">Loading comments…</p> : rows.length ? <div className="anime-comment-list">{rows.filter(row => !row.parent_comment_id).map(row => <article className="anime-comment" key={row.id}>
      <div className="anime-comment-main"><div className="anime-comment-meta"><strong>{row.author?.display_name || row.author?.username || 'Ryko reader'}</strong><time>{new Date(row.created_at).toLocaleDateString()}</time></div>
        {row.spoiler && !revealed.includes(row.id) ? <button className="spoiler-reveal" onClick={() => setRevealed(old => [...old, row.id])}>Spoiler hidden · Reveal comment</button> : <p className="anime-comment-body">{row.body}</p>}
      </div></article>)}</div> : <div className="entry-empty">No approved comments yet. Start the discussion.</div>}
  </section>;
}

type Period = 'month' | 'year' | 'all';
type TrendRow = { anilist_id: number; likes: number };
export function RykoTrendingPage() {
  const [period, setPeriod] = useState<Period>('month');
  const [items, setItems] = useState<Array<{ anime: Anime; likes: number }>>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    void (async () => {
      const { data, error: rpcError } = await supabase.rpc('get_ryko_top_trending', { period_key: period, result_limit: 30 });
      if (rpcError) { if (active) { setError(rpcError.message); setLoading(false); } return; }
      const rows = (data || []) as TrendRow[];
      const results = await Promise.all(rows.map(async row => {
        try { const anime = await getAnime(String(row.anilist_id)); return anime ? { anime, likes: Number(row.likes) } : null; }
        catch { return null; }
      }));
      if (active) { setItems(results.filter((value): value is { anime: Anime; likes: number } => !!value)); setLoading(false); }
    })();
    return () => { active = false; };
  }, [period]);
  const periodLabel = period === 'month' ? 'This month' : period === 'year' ? 'This year' : 'All time';
  return <main className="content-page ryko-trending-page">
    <div className="page-heading"><span className="eyebrow"><Trophy size={14}/> RYKO COLLECTION CHARTS</span><h1>Top <em>trending.</em></h1><p>Ranked by likes from the RykoCollection community.</p></div>
    <div className="trending-period-tabs" role="tablist" aria-label="Trending period">{(['month','year','all'] as Period[]).map(value=><button key={value} role="tab" aria-selected={period===value} className={period===value?'active':''} onClick={()=>setPeriod(value)}>{value==='month'?'Month':value==='year'?'Year':'All time'}</button>)}</div>
    <section className="settings-card"><div className="section-head"><div><span className="eyebrow">{periodLabel.toUpperCase()}</span><h2><Trophy size={18}/> Most liked entries</h2></div></div>
      {loading ? <p className="fine-print">Calculating the community chart…</p> : error ? <p className="anime-comment-error">Could not load the Ryko chart: {error}</p> : items.length ? <div className="ryko-trending-list">{items.map((item,index)=><Link to={'/anime/'+item.anime.id} className="ryko-trending-item" key={item.anime.id}><span className="ryko-trending-rank">{index+1}</span><img src={imageOf(item.anime)} alt="" loading="lazy"/><span className="ryko-trending-title"><strong>{titleOf(item.anime)}</strong><small>{item.anime.format || 'Anime / manga'}</small></span><span className="ryko-trending-likes"><Heart size={14} fill="currentColor"/>{item.likes.toLocaleString()}</span></Link>)}</div> : <p className="fine-print">No likes have been recorded for {periodLabel.toLowerCase()} yet.</p>}
    </section>
  </main>;
}
