import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import type { User } from '@supabase/supabase-js';
import {
  MessageCircle, Send, ChevronDown, ChevronUp, Clock3, Flame, Search,
  Copy, RefreshCw, Eye, EyeOff, Heart, Trophy
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { supabase } from '../lib/supabase';
import { getAnimeByAniListIds, imageOf, titleOf, type Anime } from '../lib/catalogue';

type CommentRow = {
  id: string;
  anilist_id: number;
  user_id: string;
  parent_comment_id: string | null;
  body: string;
  spoiler: boolean;
  moderation_status: string;
  created_at: string;
  author?: { username?: string; display_name?: string };
};

type CommentNode = CommentRow & { children: CommentNode[] };
type SortMode = 'newest' | 'oldest';

const relativeTime = (value: string) => {
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return 'just now';
  const seconds = Math.max(0, Math.floor((Date.now() - timestamp) / 1000));
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  if (seconds < 604800) return `${Math.floor(seconds / 86400)}d ago`;
  return new Date(value).toLocaleDateString();
};

const makeThreads = (rows: CommentRow[], sort: SortMode): CommentNode[] => {
  const nodes = new Map<string, CommentNode>();
  rows.forEach(row => nodes.set(row.id, { ...row, children: [] }));
  const roots: CommentNode[] = [];
  nodes.forEach(node => {
    const parent = node.parent_comment_id ? nodes.get(node.parent_comment_id) : undefined;
    if (parent) parent.children.push(node);
    else roots.push(node);
  });
  const sortTree = (items: CommentNode[]) => {
    items.sort((a, b) => sort === 'newest'
      ? new Date(b.created_at).getTime() - new Date(a.created_at).getTime()
      : new Date(a.created_at).getTime() - new Date(b.created_at).getTime());
    items.forEach(item => sortTree(item.children));
  };
  sortTree(roots);
  return roots;
};

export function EntryLikeButton({ animeId, user }: { animeId: string; user: User | null }) {
  const id = Number(String(animeId).match(/(\d+)$/)?.[1]);
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
  return <button type="button" className={liked ? 'secondary-btn entry-like-button liked' : 'secondary-btn entry-like-button'} onClick={() => void toggle()} disabled={!user || busy || !Number.isSafeInteger(id) || id <= 0} title={!user ? 'Sign in to like this entry' : liked ? 'Unlike this entry' : 'Like this entry'}><Heart size={15} fill={liked ? 'currentColor' : 'none'}/>{liked ? 'Liked' : 'Like'} <span>{count.toLocaleString()}</span></button>;
}

export function EntryComments({ animeId, animeTitle, user }: { animeId: string; animeTitle: string; user: User | null }) {
  const id = Number(String(animeId).match(/(\d+)$/)?.[1]);
  const [rows, setRows] = useState<CommentRow[]>([]);
  const [draft, setDraft] = useState('');
  const [spoiler, setSpoiler] = useState(false);
  const [replyTo, setReplyTo] = useState<string | null>(null);
  const [replyDraft, setReplyDraft] = useState('');
  const [revealed, setRevealed] = useState<string[]>([]);
  const [expanded, setExpanded] = useState<string[]>([]);
  const [sort, setSort] = useState<SortMode>('newest');
  const [search, setSearch] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(async (silent = false) => {
    if (!Number.isSafeInteger(id) || id <= 0) { setRows([]); setLoading(false); setError('This entry has an invalid catalogue ID.'); return; }
    if (!silent) setLoading(true);
    setError('');
    const { data, error: queryError } = await supabase.from('anime_entry_comments')
      .select('id,anilist_id,user_id,parent_comment_id,body,spoiler,moderation_status,created_at')
      .eq('anilist_id', id).eq('moderation_status', 'approved')
      .order('created_at', { ascending: false }).limit(150);
    if (queryError) { setError(queryError.message); if (!silent) setRows([]); setLoading(false); return; }
    const comments = (data || []) as CommentRow[];
    const authorIds = [...new Set(comments.map(row => row.user_id))];
    if (authorIds.length) {
      const { data: profiles, error: profileError } = await supabase.from('anime_entry_profiles')
        .select('id,username,display_name').in('id', authorIds);
      if (!profileError && profiles) {
        const byId = new Map(profiles.map(profile => [profile.id, profile]));
        comments.forEach(row => { row.author = byId.get(row.user_id); });
      }
    }
    setRows(comments);
    setLoading(false);
  }, [id]);

  useEffect(() => {
    let active = true;
    void load();
    const channel = supabase.channel(`entry-comments-${id}`)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'anime_entry_comments', filter: `anilist_id=eq.${id}` }, () => {
        if (active) void load(true);
      }).subscribe();
    return () => { active = false; void supabase.removeChannel(channel); };
  }, [id, load]);

  const threads = useMemo(() => makeThreads(rows, sort), [rows, sort]);
  const matchesThread = (node: CommentNode, term: string): boolean => {
    if (!term) return true;
    const own = `${node.body} ${node.author?.display_name || ''} ${node.author?.username || ''}`.toLowerCase().includes(term);
    return own || node.children.some(child => matchesThread(child, term));
  };
  const visibleThreads = useMemo(() => {
    const term = search.trim().toLowerCase();
    return threads.filter(node => matchesThread(node, term));
  }, [threads, search]);
  const totalReplies = rows.filter(row => !!row.parent_comment_id).length;
  const rootCount = rows.length - totalReplies;

  const submit = async (event: FormEvent, body: string, parentId: string | null) => {
    event.preventDefault();
    if (!user) { setError('Sign in to join this discussion.'); return; }
    const clean = body.trim();
    if (!clean || clean.length > 3000 || !Number.isSafeInteger(id) || id <= 0 || busy) return;
    setBusy(true); setError(''); setNotice('');
    const { error: insertError } = await supabase.from('anime_entry_comments').insert({
      anilist_id: id, user_id: user.id, body: clean, parent_comment_id: parentId,
      spoiler: parentId ? false : spoiler, moderation_status: 'pending'
    });
    if (insertError) setError(insertError.message);
    else {
      if (parentId) { setReplyDraft(''); setReplyTo(null); setExpanded(old => old.includes(parentId) ? old : [...old, parentId]); }
      else { setDraft(''); setSpoiler(false); }
      setNotice('Submitted for review. It will appear here once approved.');
    }
    setBusy(false);
  };

  const copyLink = async (commentId: string) => {
    const url = `${window.location.origin}${window.location.pathname}#comment-${commentId}`;
    try { await navigator.clipboard.writeText(url); setNotice('Comment link copied.'); }
    catch { setError('Could not copy the link on this device.'); }
  };

  const renderThread = (node: CommentNode, depth = 0): ReactNode => {
    const name = node.author?.display_name || node.author?.username || 'Ryko reader';
    const initials = name.trim().slice(0, 1).toUpperCase() || 'R';
    const isRevealed = revealed.includes(node.id);
    const isExpanded = expanded.includes(node.id);
    return <article className={depth ? 'ryko-comment ryko-comment-reply' : 'ryko-comment'} id={`comment-${node.id}`} key={node.id}>
      <div className="ryko-comment-avatar" aria-hidden="true">{initials}</div>
      <div className="ryko-comment-content">
        <div className="ryko-comment-byline">
          <strong>{name}</strong>
          {node.author?.username && <span>@{node.author.username}</span>}
          <i>·</i><time title={new Date(node.created_at).toLocaleString()}>{relativeTime(node.created_at)}</time>
          {node.spoiler && <span className="ryko-spoiler-label">SPOILER</span>}
        </div>
        {node.spoiler && !isRevealed
          ? <button type="button" className="ryko-spoiler-cover" onClick={() => setRevealed(old => [...old, node.id])}><Eye size={14}/> Spoiler hidden — tap to reveal</button>
          : <p className="ryko-comment-text">{node.body}</p>}
        <div className="ryko-comment-toolbar">
          {user && <button type="button" onClick={() => { setReplyTo(replyTo === node.id ? null : node.id); setReplyDraft(''); }}><MessageCircle size={14}/> Reply</button>}
          <button type="button" onClick={() => void copyLink(node.id)}><Copy size={13}/> Share</button>
          {node.children.length > 0 && <button type="button" onClick={() => setExpanded(old => old.includes(node.id) ? old.filter(value => value !== node.id) : [...old, node.id])}>
            {isExpanded ? <ChevronUp size={14}/> : <ChevronDown size={14}/>} {node.children.length} {node.children.length === 1 ? 'reply' : 'replies'}
          </button>}
          {node.spoiler && isRevealed && <button type="button" onClick={() => setRevealed(old => old.filter(value => value !== node.id))}><EyeOff size={13}/> Hide</button>}
        </div>
        {replyTo === node.id && <form className="ryko-reply-composer" onSubmit={event => void submit(event, replyDraft, node.id)}>
          <textarea value={replyDraft} onChange={event => setReplyDraft(event.target.value)} placeholder={`Reply to ${name}…`} maxLength={3000} required/>
          <div><small>{replyDraft.length}/3000 · Replies are reviewed before publishing</small><button className="primary-btn" disabled={busy || !replyDraft.trim()}><Send size={14}/>{busy ? 'Sending…' : 'Reply'}</button></div>
        </form>}
        {node.children.length > 0 && isExpanded && <div className="ryko-comment-thread">{node.children.map(child => renderThread(child, depth + 1))}</div>}
      </div>
    </article>;
  };

  return <section className="anime-comments entry-comments ryko-discussion">
    <div className="ryko-discussion-heading">
      <div><span className="eyebrow"><Flame size={13}/> RYKO COMMUNITY</span><h2>Join the discussion</h2><p>Share your take on <strong>{animeTitle}</strong>. Keep it thoughtful and spoiler-aware.</p></div>
      <div className="ryko-discussion-stats"><strong>{rootCount.toLocaleString()}</strong><span>comments</span><i>·</i><strong>{totalReplies.toLocaleString()}</strong><span>replies</span></div>
    </div>

    {user ? <form className="ryko-comment-composer" onSubmit={event => void submit(event, draft, null)}>
      <div className="ryko-composer-avatar">{(user.user_metadata?.display_name || user.user_metadata?.username || user.email || 'R').toString().slice(0, 1).toUpperCase()}</div>
      <div className="ryko-composer-main">
        <textarea value={draft} onChange={event => setDraft(event.target.value)} placeholder="What are your thoughts? Start a conversation…" maxLength={3000} required/>
        <div className="ryko-composer-footer">
          <label><input type="checkbox" checked={spoiler} onChange={event => setSpoiler(event.target.checked)}/> <Eye size={13}/> Contains spoilers</label>
          <span>{draft.length}/3000</span>
          <button className="primary-btn" disabled={busy || !draft.trim()}><Send size={14}/>{busy ? 'Posting…' : 'Post'}</button>
        </div>
      </div>
    </form> : <div className="ryko-signin-prompt"><MessageCircle size={18}/><span><strong>Be part of the conversation</strong><small>Sign in to post comments and reply to other readers.</small></span></div>}

    {(error || notice) && <p className={error ? 'anime-comment-error' : 'anime-comment-success'} role="status">{error || notice}</p>}

    <div className="ryko-discussion-tools">
      <div className="ryko-discussion-sort">
        <button type="button" className={sort === 'newest' ? 'active' : ''} onClick={() => setSort('newest')}><Clock3 size={13}/> Newest</button>
        <button type="button" className={sort === 'oldest' ? 'active' : ''} onClick={() => setSort('oldest')}><Flame size={13}/> Oldest</button>
      </div>
      <label className="ryko-comment-search"><Search size={14}/><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Search discussion"/></label>
      <button type="button" className="ryko-refresh-comments" onClick={() => void load()} disabled={loading} aria-label="Refresh comments"><RefreshCw size={14}/></button>
    </div>

    {loading ? <div className="anime-comments-loading"><span className="ryko-comment-skeleton"/><span className="ryko-comment-skeleton"/><span className="ryko-comment-skeleton"/></div>
      : error && !rows.length ? <div className="ryko-comments-empty"><MessageCircle size={22}/><strong>Discussion unavailable</strong><p>{error}</p><button className="secondary-btn" onClick={() => void load()}>Try again</button></div>
      : visibleThreads.length ? <div className="ryko-comment-feed">{visibleThreads.map(node => renderThread(node))}</div>
      : <div className="ryko-comments-empty"><MessageCircle size={24}/><strong>{search ? 'No matching comments' : 'Be the first to start a conversation'}</strong><p>{search ? 'Try a different search term.' : 'Your perspective can make this discussion better.'}</p></div>}
    <p className="ryko-moderation-note">New comments and replies are checked before they appear. Please be respectful of other fans.</p>
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
      const entries = await getAnimeByAniListIds(rows.map(row => Number(row.anilist_id)));
      const byId = new Map(entries.map(anime => [anime.anilistId, anime]));
      const results = rows.flatMap(row => {
        const anime = byId.get(Number(row.anilist_id));
        return anime ? [{ anime, likes: Number(row.likes) }] : [];
      });
      if (active) { setItems(results); setLoading(false); }
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
