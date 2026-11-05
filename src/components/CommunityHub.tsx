import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { supabase } from '../lib/supabase';
import type { User } from '@supabase/supabase-js';
import { ShieldCheck, MessageCircle, UserRound, Send, AlertTriangle } from 'lucide-react';

type CommunityPost = {
  id: string; anilist_id: number; user_id: string; parent_comment_id: string | null;
  body: string; spoiler: boolean; moderation_status: 'pending'|'approved'|'rejected';
  created_at: string;
  anime_entry_profiles?: { username?: string; display_name?: string; avatar_url?: string } | null;
};

const GUIDELINES = [
  'Keep discussions respectful. No harassment, hate speech, threats, bullying or personal attacks.',
  'No explicit sexual content, pornography, nudity, graphic sexual descriptions or links to explicit material.',
  'No spam, scams, malicious links, impersonation, doxxing or sharing someone’s private information.',
  'Mark plot reveals and major story details as spoilers. Do not put spoiler text in titles.',
  'Critique stories and ideas, not people. Repeated abuse may lead to post removal or account restrictions.',
  'Only share content you have the right to post. Report unsafe posts instead of engaging with them.'
];

function communityTextCheck(value: string) {
  const text = value.toLowerCase();
  const blocked = ['porn','xxx','hentai','nudes','onlyfans','kill yourself','go die','doxx'];
  if (blocked.some(term => text.includes(term))) return 'This text appears to violate the Community Guidelines.';
  if ((text.match(/https?:\/\//g) || []).length > 2) return 'Please remove repeated links.';
  if (/(.)\\1{11,}/i.test(text)) return 'Please remove repeated characters.';
  return '';
}

export function CommunityHub({ user, isAdmin = false }: { user: User | null; isAdmin?: boolean }) {
  const [anilistId, setAnilistId] = useState('');
  const [posts, setPosts] = useState<CommunityPost[]>([]);
  const [body, setBody] = useState('');
  const [spoiler, setSpoiler] = useState(false);
  const [profile, setProfile] = useState({ username: '', display_name: '', bio: '', avatar_url: '', banner_url: '' });
  const [status, setStatus] = useState('');
  const [busy, setBusy] = useState(false);
  const [revealed, setRevealed] = useState<string[]>([]);
  const [guidelinesOpen, setGuidelinesOpen] = useState(true);
  const [pendingPosts, setPendingPosts] = useState<CommunityPost[]>([]);

  const loadPosts = async () => {
    const id = Number(anilistId);
    if (!Number.isSafeInteger(id) || id <= 0) { setPosts([]); return; }
    const { data, error } = await supabase.from('anime_entry_comments')
      .select('id,anilist_id,user_id,parent_comment_id,body,spoiler,moderation_status,created_at,anime_entry_profiles(username,display_name,avatar_url)')
      .eq('anilist_id', id).eq('moderation_status', 'approved')
      .is('parent_comment_id', null).order('created_at', { ascending: false }).limit(40);
    if (error) { setStatus(error.message); return; }
    setPosts((data || []) as unknown as CommunityPost[]);
  };

  useEffect(() => { void loadPosts(); }, [anilistId]);
  const loadPending = async () => {
    if (!isAdmin) return;
    const { data, error } = await supabase.from('anime_entry_comments')
      .select('id,anilist_id,user_id,parent_comment_id,body,spoiler,moderation_status,created_at')
      .eq('moderation_status', 'pending').order('created_at', { ascending: true }).limit(100);
    if (error) { setStatus(error.message); return; }
    setPendingPosts((data || []) as CommunityPost[]);
  };
  useEffect(() => { void loadPending(); }, [isAdmin]);
  const moderate = async (post: CommunityPost, decision: 'approved' | 'rejected') => {
    const { error } = await supabase.from('anime_entry_comments')
      .update({ moderation_status: decision, moderation_reason: decision === 'rejected' ? 'Did not meet Community Guidelines' : null })
      .eq('id', post.id);
    if (error) { setStatus(error.message); return; }
    setPendingPosts(old => old.filter(item => item.id !== post.id));
    if (String(post.anilist_id) === anilistId) void loadPosts();
  };

  useEffect(() => {
    if (!user) return;
    let active = true;
    void supabase.from('anime_entry_profiles').select('username,display_name').eq('id', user.id).maybeSingle().then(({ data }) => {
      if (active && data) setProfile(old => ({ ...old, username: data.username || '', display_name: data.display_name || '' }));
    });
    void supabase.from('profiles').select('bio,avatar_url,banner_url').eq('id', user.id).maybeSingle().then(({ data }) => {
      if (active && data) setProfile(old => ({ ...old, bio: data.bio || '', avatar_url: data.avatar_url || '', banner_url: data.banner_url || '' }));
    });
    return () => { active = false; };
  }, [user?.id]);

  const submitPost = async (event: FormEvent) => {
    event.preventDefault();
    if (!user) { setStatus('Sign in to join the discussion.'); return; }
    const id = Number(anilistId);
    if (!Number.isSafeInteger(id) || id <= 0) { setStatus('Enter a valid AniList title ID.'); return; }
    const clean = body.trim();
    const warning = communityTextCheck(clean);
    if (warning) { setStatus(warning); return; }
    if (!clean || clean.length > 3000) { setStatus('Write between 1 and 3000 characters.'); return; }
    setBusy(true); setStatus('');
    const { error } = await supabase.from('anime_entry_comments').insert({
      anilist_id: id, user_id: user.id, body: clean, spoiler, moderation_status: 'pending'
    });
    setBusy(false);
    if (error) { setStatus(error.message); return; }
    setBody(''); setSpoiler(false); setStatus('Submitted for moderation. It will appear after approval.');
  };

  const saveProfile = async (event: FormEvent) => {
    event.preventDefault();
    if (!user) return;
    const username = profile.username.trim().toLowerCase();
    if (!/^[a-z0-9_]{2,32}$/.test(username)) { setStatus('Username must be 2–32 characters: letters, numbers or underscores.'); return; }
    setBusy(true); setStatus('');
    const [entryResult, profileResult] = await Promise.all([
      supabase.from('anime_entry_profiles').update({ username, display_name: profile.display_name.trim() || 'Reader' }).eq('id', user.id),
      supabase.from('profiles').update({ bio: profile.bio.slice(0,280), avatar_url: profile.avatar_url, banner_url: profile.banner_url }).eq('id', user.id)
    ]);
    setBusy(false);
    const error = entryResult.error || profileResult.error;
    setStatus(error ? error.message : 'Profile saved.');
  };

  return <main className="content-page community-page">
    <div className="page-heading"><span className="eyebrow">RYKO COMMUNITY</span><h1>Community <em>space.</em></h1><p>Talk about anime and manga in a safer, moderated space.</p></div>
    <section className="settings-card community-guidelines">
      <button className="section-head" type="button" onClick={() => setGuidelinesOpen(v => !v)} aria-expanded={guidelinesOpen}>
        <div><span className="eyebrow">COMMUNITY SAFETY</span><h2><ShieldCheck size={19}/> Community Guidelines</h2></div><span>{guidelinesOpen ? 'Hide' : 'Read guidelines'}</span>
      </button>
      {guidelinesOpen && <><ul>{GUIDELINES.map((item, index) => <li key={index}>{item}</li>)}</ul><p className="fine-print"><AlertTriangle size={14}/> New discussions are held for moderation. Posts that violate these rules can be rejected or removed.</p></>}
    </section>
    <section className="settings-card">
      <div className="section-head"><div><span className="eyebrow">TITLE DISCUSSIONS</span><h2><MessageCircle size={18}/> Anime & manga discussions</h2></div></div>
      <label className="community-field">AniList title ID<input inputMode="numeric" value={anilistId} onChange={e => setAnilistId(e.target.value.replace(/[^0-9]/g,''))} placeholder="Enter the AniList ID"/></label>
      <p className="fine-print">Open a title on AniList and use the number in its URL. Only approved posts are shown publicly.</p>
      {user ? <form className="community-compose" onSubmit={submitPost}>
        <textarea value={body} onChange={e => setBody(e.target.value)} maxLength={3000} placeholder="Share a respectful thought about this title…" required/>
        <label><input type="checkbox" checked={spoiler} onChange={e => setSpoiler(e.target.checked)}/> Contains spoilers</label>
        <div className="community-compose-foot"><small>{body.length}/3000 · New posts are reviewed</small><button className="primary-btn" disabled={busy || !body.trim()}><Send size={15}/>{busy ? 'Submitting…' : 'Submit post'}</button></div>
      </form> : <p className="fine-print">Sign in from <Link to="/account">Account</Link> to post or manage your profile.</p>}
      {status && <p className="account-message" role="status">{status}</p>}
      <div className="community-post-list">{posts.map(post => {
        const isRevealed = revealed.includes(post.id);
        return <article className="community-post" key={post.id}>
          <div className="community-post-author"><span className="account-avatar"><UserRound size={17}/></span><strong>{post.anime_entry_profiles?.display_name || post.anime_entry_profiles?.username || 'Reader'}</strong><small>{new Date(post.created_at).toLocaleDateString()}</small></div>
          {post.spoiler && !isRevealed ? <button className="spoiler-reveal" onClick={() => setRevealed(old => [...old, post.id])}>Spoiler hidden · Tap to reveal</button> : <p>{post.body}</p>}
        </article>;
      })}{anilistId && !posts.length && <p className="fine-print">No approved discussions yet for this title.</p>}</div>
    </section>
    {isAdmin && <section className="settings-card">
      <div className="section-head"><div><span className="eyebrow">MODERATION QUEUE</span><h2><ShieldCheck size={18}/> Pending community posts</h2></div><button className="secondary-btn" onClick={()=>void loadPending()}>Refresh</button></div>
      {pendingPosts.length ? <div className="community-post-list">{pendingPosts.map(post=><article className="community-post" key={post.id}><small>AniList ID {post.anilist_id} · {new Date(post.created_at).toLocaleString()}</small><p>{post.spoiler && <strong>Possible spoiler · </strong>}{post.body}</p><div className="community-compose-foot"><button className="secondary-btn" onClick={()=>void moderate(post,'rejected')}>Reject</button><button className="primary-btn" onClick={()=>void moderate(post,'approved')}>Approve</button></div></article>)}</div> : <p className="fine-print">No pending posts.</p>}
    </section>}
    {user && <section className="settings-card">
      <div className="section-head"><div><span className="eyebrow">PUBLIC READER PROFILE</span><h2><UserRound size={18}/> Your profile</h2></div></div>
      <form className="community-profile-form" onSubmit={saveProfile}>
        <label>Username<input value={profile.username} onChange={e=>setProfile(v=>({...v,username:e.target.value}))} maxLength={32} placeholder="reader_name"/></label>
        <label>Display name<input value={profile.display_name} onChange={e=>setProfile(v=>({...v,display_name:e.target.value}))} maxLength={60} placeholder="Reader"/></label>
        <label>Bio<textarea value={profile.bio} onChange={e=>setProfile(v=>({...v,bio:e.target.value}))} maxLength={280} placeholder="A little about your anime and manga interests"/></label>
        <label>Avatar image URL<input value={profile.avatar_url} onChange={e=>setProfile(v=>({...v,avatar_url:e.target.value}))} placeholder="https://…"/></label>
        <label>Banner image URL<input value={profile.banner_url} onChange={e=>setProfile(v=>({...v,banner_url:e.target.value}))} placeholder="https://…"/></label>
        <button className="primary-btn" disabled={busy}>Save profile</button>
      </form>
    </section>}
  </main>;
}
