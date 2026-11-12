"""Opt-in officer-reported clan activity. Never accepts public self-reported promotions."""
import asyncio
import hmac
import json
import os
from pathlib import Path
import re
import sqlite3
import time

import discord
from discord.ext import commands
from aiohttp import web

RETENTION = 7 * 86400
FRESHNESS = 180
MAX_LINKS = 500


def key(value):
    return re.sub(r'[^a-z0-9]', '', str(value).lower())


def account(value):
    name = str(value).replace('\xa0', ' ').replace('_', ' ').strip()
    if not re.fullmatch(r'[A-Za-z0-9 ]{1,12}', name):
        raise ValueError('Use a RuneScape display name of 1–12 letters, numbers or spaces.')
    return name.lower()


class ActivityStore:
    def __init__(self, path):
        self.db = sqlite3.connect(path)
        self.db.execute('PRAGMA foreign_keys=ON')
        self.db.execute('PRAGMA secure_delete=ON')
        self.db.executescript('''
            CREATE TABLE IF NOT EXISTS links (
                rsn TEXT PRIMARY KEY, discord_id TEXT NOT NULL UNIQUE);
            CREATE TABLE IF NOT EXISTS current (
                rsn TEXT PRIMARY KEY REFERENCES links(rsn) ON DELETE CASCADE,
                presence TEXT NOT NULL, rank_title TEXT, observed_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS history (
                rsn TEXT REFERENCES links(rsn) ON DELETE CASCADE,
                at INTEGER NOT NULL, presence TEXT NOT NULL, rank_title TEXT,
                PRIMARY KEY (rsn, at));
            CREATE INDEX IF NOT EXISTS history_age ON history(at);
        ''')
        self.collector = None
        self.collector_seen = 0

    def close(self):
        self.db.close()

    def link(self, rsn, discord_id):
        rsn = account(rsn)
        if not re.fullmatch(r'[0-9]{5,24}', str(discord_id)):
            raise ValueError('Use a Discord member ID.')
        if self.db.execute('SELECT 1 FROM links WHERE rsn=? AND discord_id=?', (rsn, str(discord_id))).fetchone():
            return
        if self.db.execute('SELECT COUNT(*) FROM links').fetchone()[0] >= MAX_LINKS:
            raise ValueError('Maximum linked members reached.')
        try:
            with self.db:
                self.db.execute('INSERT INTO links VALUES (?,?)', (rsn, str(discord_id)))
        except sqlite3.IntegrityError:
            raise ValueError('This account or Discord member is already linked. Unlink explicitly before replacing it.') from None

    def unlink(self, discord_id):
        with self.db:
            self.db.execute('DELETE FROM links WHERE discord_id=?', (str(discord_id),))

    def links(self):
        return dict(self.db.execute('SELECT rsn,discord_id FROM links'))

    def snapshot(self, data, expected_clan, now=None):
        now = int(time.time() if now is None else now)
        if (not isinstance(data, dict) or set(data) - {'clan','collector','available','members'}
                or key(data.get('clan', '')) != key(expected_clan)):
            raise ValueError('Wrong clan.')
        collector = data.get('collector')
        if not isinstance(collector, str) or not re.fullmatch(r'[A-Za-z0-9-]{16,80}', collector):
            raise ValueError('Invalid reporter session.')
        if self.collector and collector != self.collector and now - self.collector_seen < FRESHNESS:
            raise ValueError('Another reporter is active. Keep one authorised reporter running.')
        if type(data.get('available')) is not bool or not isinstance(data.get('members'), list) or len(data['members']) > MAX_LINKS:
            raise ValueError('Invalid snapshot.')
        members = {}
        for item in data['members']:
            if not isinstance(item, dict) or set(item) - {'name', 'present', 'rank'}:
                raise ValueError('Unsupported member fields.')
            name = account(item.get('name', ''))
            if name in members or type(item.get('present')) not in (bool, type(None)):
                raise ValueError('Invalid member observation.')
            title = item.get('rank')
            if title is not None and (not isinstance(title, str) or not title.strip() or len(title) > 80):
                raise ValueError('Invalid rank title.')
            members[name] = item
        self.collector, self.collector_seen = collector, now
        with self.db:
            for name in self.links():
                item = members.get(name) if data['available'] else None
                presence = ('observed_online' if item['present'] else 'not_observed') if item and item['present'] is not None else 'unknown'
                title = item.get('rank') if item else None
                old = self.db.execute('SELECT presence,rank_title FROM current WHERE rsn=?', (name,)).fetchone()
                self.db.execute('INSERT OR REPLACE INTO current VALUES (?,?,?,?)', (name, presence, title, now))
                if old != (presence, title):
                    self.db.execute('INSERT OR REPLACE INTO history VALUES (?,?,?,?)', (name, now, presence, title))
        self.purge(now)
        return len(self.links())

    def purge(self, now=None):
        now = int(time.time() if now is None else now)
        cutoff = now - RETENTION
        with self.db:
            for name, observed in self.db.execute("SELECT rsn,observed_at FROM current WHERE presence != 'unknown' AND observed_at<=?", (now - FRESHNESS,)).fetchall():
                self.db.execute('INSERT OR REPLACE INTO history VALUES (?,?,?,?)', (name, observed + FRESHNESS, 'unknown', None))
                self.db.execute("UPDATE current SET presence='unknown',rank_title=NULL WHERE rsn=?", (name,))
            # Carry forward only the boundary state, never the older activity timestamp.
            for name in self.links():
                boundary = self.db.execute('SELECT presence,rank_title FROM history WHERE rsn=? AND at<? ORDER BY at DESC LIMIT 1', (name, cutoff)).fetchone()
                if boundary:
                    self.db.execute('INSERT OR IGNORE INTO history VALUES (?,?,?,?)', (name, cutoff, *boundary))
            self.db.execute('DELETE FROM history WHERE at<?', (cutoff,))
            self.db.execute('DELETE FROM current WHERE observed_at<?', (cutoff,))

    def observations(self, now=None):
        now = int(time.time() if now is None else now)
        self.purge(now)
        return list(self.db.execute('SELECT links.rsn,links.discord_id,current.presence,current.rank_title,current.observed_at '
            'FROM links JOIN current USING(rsn) WHERE current.observed_at>?', (now - FRESHNESS,)))

    def history(self, discord_id, now=None):
        now = int(time.time() if now is None else now)
        self.purge(now)
        return list(self.db.execute('SELECT history.at,history.presence,history.rank_title FROM history JOIN links USING(rsn) '
            'WHERE links.discord_id=? AND history.at>? ORDER BY history.at DESC LIMIT 30', (str(discord_id), now - RETENTION)))


def role_plan(observations, mapping, member_roles, role_details, bot_top, can_manage):
    """Pure dry-run planner. Touch only mapped non-staff roles below the bot."""
    managed = {str(value) for value in mapping.values()}
    dangerous = (1 << 3) | (1 << 4) | (1 << 5) | (1 << 28) | (1 << 1) | (1 << 2) | (1 << 40) | (1 << 29) | (1 << 13)
    plans = []
    for rsn, user, presence, title, observed in observations:
        desired = mapping.get(key(title)) if title else None
        if not desired or user not in member_roles:
            continue
        role = role_details.get(str(desired))
        if not role or role['managed'] or int(role['permissions']) & dangerous:
            continue
        current = set(map(str, member_roles[user]))
        add = [] if str(desired) in current else [str(desired)]
        remove = []
        blocked = not can_manage or role['position'] >= bot_top
        for old in current & managed - {str(desired)}:
            info = role_details.get(old)
            if not info or info['managed'] or int(info['permissions']) & dangerous or info['position'] >= bot_top:
                blocked = True
            else:
                remove.append(old)
        if add or remove:
            plans.append({'discord_id': user, 'rsn': rsn, 'add': add, 'remove': remove, 'blocked': blocked})
    return plans


def install(bot, root):
    if os.getenv('CLAN_SYNC_ENABLED', '').lower() != 'true':
        return
    root = Path(root)
    config = json.loads((root / 'clan_sync_roles.json').read_text(encoding='utf-8'))
    guild_id, clan = int(config['guild_id']), config['clan']
    mapping = {key(k): str(v) for k,v in config['rank_roles'].items()}
    token = os.getenv('CLAN_SYNC_REPORTER_TOKEN', '')
    if len(token) < 32:
        raise ValueError('Configure a private reporter token of at least 32 characters.')
    original_setup, original_close = bot.setup_hook, bot.close
    runner, maintenance, store = None, None, None
    role_lock = asyncio.Lock()
    staff_channel = os.getenv('CLAN_SYNC_STAFF_CHANNEL_ID', '')
    last_applied = {}

    def authorised(request):
        return hmac.compare_digest(request.headers.get('Authorization', ''), 'Bearer ' + token)

    async def links(request):
        if not authorised(request): raise web.HTTPUnauthorized()
        return web.json_response({'clan': clan, 'members': list(store.links())}, headers={'Cache-Control':'no-store'})

    async def snapshot(request):
        if not authorised(request): raise web.HTTPUnauthorized()
        try:
            count = store.snapshot(await request.json(), clan)
        except (ValueError, TypeError, KeyError):
            raise web.HTTPBadRequest(text='Invalid snapshot or another reporter is active.') from None
        return web.json_response({'accepted':count, 'mode':'live' if os.getenv('CLAN_SYNC_APPLY_ROLES','').lower()=='true' else 'dry-run'}, headers={'Cache-Control':'no-store'})

    async def plans(force=False):
        async with role_lock:
            guild = bot.get_guild(guild_id)
            if not guild or not guild.me: return [], {}
            observations = store.observations()
            details = {str(r.id): {'managed':r.managed, 'permissions':r.permissions.value, 'position':r.position} for r in guild.roles}
            members = {}
            for row in observations:
                user, title = row[1], row[3]
                desired = mapping.get(key(title)) if title else None
                cached = last_applied.get(user)
                if not desired or (not force and cached and cached[0] == desired and time.time() - cached[1] < 600): continue
                try: members[user] = await guild.fetch_member(int(user))
                except discord.NotFound: continue
            planned = role_plan(observations, mapping, {u:[r.id for r in m.roles] for u,m in members.items()}, details,
                guild.me.top_role.position, guild.me.guild_permissions.manage_roles)
            if os.getenv('CLAN_SYNC_APPLY_ROLES','').lower() == 'true' and not force:
                applied_users = set()
                for plan in planned:
                    if plan['blocked']: continue
                    member = members[plan['discord_id']]
                    for role_id in plan['add']: await member.add_roles(guild.get_role(int(role_id)), reason='Officer-linked in-game clan rank sync')
                    for role_id in plan['remove']: await member.remove_roles(guild.get_role(int(role_id)), reason='Replace mapped clan rank role')
                    applied_users.add(plan['discord_id'])
                blocked_users = {p['discord_id'] for p in planned if p['blocked']}
                for row in observations:
                    desired = mapping.get(key(row[3] or ''))
                    if row[1] in members and row[1] not in blocked_users and desired:
                        actual = {str(r.id) for r in members[row[1]].roles}
                        already_correct = actual & set(mapping.values()) == {desired}
                        if row[1] in applied_users or already_correct:
                            last_applied[row[1]] = (desired,time.time())
            return planned, members

    async def loop():
        await bot.wait_until_ready()
        while True:
            try:
                store.purge()
                if os.getenv('CLAN_SYNC_APPLY_ROLES','').lower() == 'true': await plans()
            except (discord.HTTPException, sqlite3.Error):
                import logging
                logging.getLogger('f2s').warning('Clan sync update unavailable; no roster or credentials logged.')
            await asyncio.sleep(60)

    async def setup():
        nonlocal runner, maintenance, store
        store = ActivityStore(root / 'clan_activity.sqlite3')
        app = web.Application(client_max_size=65536)
        app.router.add_get('/v1/clan/links', links)
        app.router.add_post('/v1/clan/snapshot', snapshot)
        runner = web.AppRunner(app, access_log=None)
        await runner.setup()
        try:
            await web.TCPSite(runner, '127.0.0.1', int(os.getenv('CLAN_SYNC_PORT','8788'))).start()
            await original_setup()
            maintenance = asyncio.create_task(loop())
        except BaseException:
            await runner.cleanup(); store.close(); raise

    async def close():
        if maintenance:
            maintenance.cancel()
            try: await maintenance
            except asyncio.CancelledError: pass
        if runner: await runner.cleanup()
        if store: store.close()
        await original_close()

    async def officer(ctx):
        allowed = {x.strip() for x in os.getenv('CLAN_SYNC_OFFICER_ROLE_IDS','').split(',') if x.strip()}
        return (ctx.guild is not None and ctx.guild.id == guild_id and staff_channel.isdecimal()
            and ctx.channel.id == int(staff_channel) and (ctx.author.guild_permissions.manage_guild
                or any(str(r.id) in allowed for r in ctx.author.roles)))

    @bot.command(name='clanlink')
    @commands.check(officer)
    @commands.cooldown(1, 10, commands.BucketType.user)
    async def clanlink(ctx, discord_id: str, *, rsn: str):
        user = discord_id.strip('<@!>')
        if not user.isdecimal(): return await ctx.send('Supply a Discord member ID or mention.', allowed_mentions=discord.AllowedMentions.none())
        await ctx.guild.fetch_member(int(user))
        try: store.link(rsn,user)
        except ValueError as exc: return await ctx.send(str(exc), allowed_mentions=discord.AllowedMentions.none())
        last_applied.pop(user,None)
        await ctx.send('Officer-approved link saved. Observed activity is retained for 7 days; unlink deletes its stored activity.', allowed_mentions=discord.AllowedMentions.none())

    @bot.command(name='clanunlink')
    @commands.check(officer)
    @commands.cooldown(1, 10, commands.BucketType.user)
    async def clanunlink(ctx, discord_id: str):
        user = discord_id.strip('<@!>')
        store.unlink(user); last_applied.pop(user,None)
        await ctx.send('Link and stored activity removed. Discord roles were left unchanged.', allowed_mentions=discord.AllowedMentions.none())

    @bot.command(name='clanactivity')
    @commands.check(officer)
    @commands.cooldown(1, 15, commands.BucketType.user)
    async def clanactivity(ctx, discord_id: str):
        rows = store.history(discord_id.strip('<@!>'))
        lines = [f'<t:{at}:f>: {presence.replace("_"," ")}' for at,presence,rank in rows]
        await ctx.send(('Last 7 days (up to 30 transitions):\n' + '\n'.join(lines))[:1900] if lines else 'No retained observations.', allowed_mentions=discord.AllowedMentions.none())

    @bot.command(name='clanrankpreview')
    @commands.check(officer)
    @commands.cooldown(1, 30, commands.BucketType.guild)
    async def clanrankpreview(ctx):
        planned,_ = await plans(force=True)
        lines = [f'Member {p["discord_id"]}: add {",".join(p["add"]) or "none"}; remove {",".join(p["remove"]) or "none"}; {"BLOCKED" if p["blocked"] else "ready"}' for p in planned]
        await ctx.send(('Dry-run only:\n'+'\n'.join(lines))[:1900] if lines else 'No mapped role changes from fresh linked observations.', allowed_mentions=discord.AllowedMentions.none())

    @bot.command(name='clansyncstatus')
    @commands.check(officer)
    @commands.cooldown(1, 15, commands.BucketType.guild)
    async def clansyncstatus(ctx):
        await ctx.send(f'Linked accounts: {len(store.links())}. Fresh observations: {len(store.observations())}. '
            f'Mode: {"live" if os.getenv("CLAN_SYNC_APPLY_ROLES", "").lower()=="true" else "dry-run"}. '
            'Absence from the channel is not proof of global offline status.', allowed_mentions=discord.AllowedMentions.none())

    bot.setup_hook, bot.close = setup, close
