"""F2S Bounty Bot. Google Sheets access is strictly read-only."""
import argparse
import asyncio
import json
import hashlib
import logging
import os
from pathlib import Path
import random
import re
import time
from decimal import Decimal, InvalidOperation
from datetime import datetime, timedelta, timezone
from urllib.parse import quote
from zoneinfo import ZoneInfo

import discord
from discord.ext import commands, tasks
from dotenv import load_dotenv
from google.oauth2.service_account import Credentials
from google.auth.transport.requests import AuthorizedSession

ROOT = Path(__file__).resolve().parent
load_dotenv(ROOT / '.env')
LOG = logging.getLogger('f2s')
INTERVAL = 6 * 60 * 60
SCORE_INTERVAL = 12 * 60 * 60
ANNOUNCEMENT_MENTIONS = discord.AllowedMentions(
    everyone=True, users=False, roles=False, replied_user=False)
LIST_FILES = ('bounties.json', 'daily_bounties.json', 'bonus_bounties.json')
BACKUP_FILES = LIST_FILES + ('state.json',)


def next_uk_midnight(now=None):
    zone = ZoneInfo('Europe/London')
    local = datetime.fromtimestamp(time.time() if now is None else now, zone)
    tomorrow = local.date() + timedelta(days=1)
    return int(datetime.combine(tomorrow, datetime.min.time(), tzinfo=zone).timestamp())


def load_pool(filename):
    if filename == 'bounties.json':
        return [entry['text'] for entry in load_bounties()]
    pool = json.loads((ROOT / filename).read_text(encoding='utf-8-sig'))
    return validate_text_pool(filename, pool)


def validate_text_pool(filename, pool):
    if not isinstance(pool, list) or not pool or any(
            not isinstance(x, str) or not x.strip() or len(x) > 1500 for x in pool):
        raise ValueError(f'{filename} must be a nonempty JSON list of text entries under 1500 characters.')
    return pool


def bounty_record(value):
    # Old string lists remain readable; explicit IDs survive wording changes.
    if isinstance(value, str):
        value = {'id': 'L-' + hashlib.sha256(value.encode('utf-8')).hexdigest()[:12].upper(),
                 'text': value}
    if (not isinstance(value, dict) or not isinstance(value.get('id'), str)
            or not re.fullmatch(r'[A-Z][A-Z0-9_-]{1,31}', value['id'])
            or not isinstance(value.get('text'), str) or not value['text'].strip()
            or len(value['text']) > 1500):
        raise ValueError('Each regular bounty needs a unique id and nonempty text under 1500 characters.')
    return {'id': value['id'], 'text': value['text']}


def load_bounties():
    values = json.loads((ROOT / 'bounties.json').read_text(encoding='utf-8-sig'))
    return parse_bounties(values)


def parse_bounties(values):
    if not isinstance(values, list) or not values:
        raise ValueError('bounties.json must be a nonempty list.')
    records = [bounty_record(value) for value in values]
    if len({record['id'] for record in records}) != len(records):
        # Identical legacy strings are harmless duplicates; explicit IDs must be unique.
        explicit = [value['id'] for value in values if isinstance(value, dict)]
        if len(set(explicit)) != len(explicit):
            raise ValueError('Duplicate bounty IDs in bounties.json.')
        records = list({record['id']: record for record in records}.values())
    return records


def checklist_report():
    results = []
    for name in LIST_FILES:
        try:
            pool = load_bounties() if name == 'bounties.json' else load_pool(name)
            results.append({'name': name, 'ok': True, 'count': len(pool),
                            'message': f'{len(pool)} valid entries'})
        except json.JSONDecodeError as exc:
            results.append({'name': name, 'ok': False,
                            'message': f'Invalid JSON at line {exc.lineno}, column {exc.colno}: {exc.msg}'})
        except FileNotFoundError:
            results.append({'name': name, 'ok': False, 'message': 'File is missing.'})
        except UnicodeError:
            results.append({'name': name, 'ok': False, 'message': 'Invalid encoding; save as UTF-8.'})
        except ValueError as exc:
            results.append({'name': name, 'ok': False, 'message': str(exc)})
        except OSError as exc:
            results.append({'name': name, 'ok': False, 'message': f'Unable to read file ({type(exc).__name__}).'})
    return results


def backup_current_files():
    """Keep up to 20 valid changed versions per file; never copy credentials."""
    result = {'saved': 0, 'skipped': []}
    directory = ROOT / 'backups'
    try:
        directory.mkdir(exist_ok=True)
        resolved = directory.resolve()
        if resolved.parent != ROOT.resolve() or directory.is_symlink():
            raise ValueError('Backup directory must stay inside the bot folder.')
        for name in BACKUP_FILES:
            source = ROOT / name
            if not source.exists():
                continue
            try:
                raw = source.read_bytes()
                data = json.loads(raw.decode('utf-8-sig'))
                if name == 'bounties.json':
                    parse_bounties(data)
                elif name in LIST_FILES:
                    validate_text_pool(name, data)
                elif not isinstance(data, dict):
                    raise ValueError('State must be a JSON object.')
                digest = hashlib.sha256(raw).hexdigest()[:16]
                stem = Path(name).stem
                if any(directory.glob(f'{stem}-*-{digest}.json')):
                    continue
                stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
                destination = directory / f'{stem}-{stamp}-{digest}.json'
                destination.write_bytes(raw)
                result['saved'] += 1
                versions = sorted(directory.glob(f'{stem}-*.json'), key=lambda path: path.name)
                for old in versions[:-20]:
                    # Only prune verified files immediately inside this backup folder.
                    if not old.is_symlink() and old.is_file() and old.resolve().parent == resolved:
                        old.unlink()
            except (OSError, ValueError, UnicodeError) as exc:
                result['skipped'].append(name)
                LOG.warning('Backup skipped for %s (%s).', name, type(exc).__name__)
    except (OSError, ValueError) as exc:
        result['skipped'] = list(BACKUP_FILES)
        LOG.warning('Backup check failed (%s).', type(exc).__name__)
    return result


class BonusOwnerOnly(commands.CheckFailure):
    pass


async def bonus_owner_check(ctx):
    configured = os.getenv('BONUS_OWNER_ID', '').strip()
    if not configured.isdecimal() or int(configured) <= 0 or ctx.author.id != int(configured):
        raise BonusOwnerOnly('Only the configured account may use !bonus.')
    return True


def setting(name):
    value = os.getenv(name, '').strip()
    if not value:
        raise ValueError(f'Set {name} in .env first.')
    return value


def text_pages(lines):
    pages, current = [], ''
    for line in lines:
        if current and len(current) + len(line) + 1 > 1800:
            pages.append(current)
            current = ''
        current += ('\n' if current else '') + line
    return pages + ([current] if current else [])


def read_scores():
    """Read evaluated numeric values; never issue a write request."""
    ranges = [setting('TEAM_1_RANGE'), setting('TEAM_2_RANGE')]
    if any(not re.fullmatch(r"(?:'[^']*(?:''[^']*)*'|[^!]+)!\$?[A-Za-z]+\$?[1-9][0-9]*", r) for r in ranges):
        raise ValueError('Score ranges must each identify one cell, including the tab name.')
    if ranges[0] == ranges[1]:
        raise ValueError('Team score cells must be different.')
    path = Path(setting('GOOGLE_CREDENTIALS_FILE'))
    if not path.is_absolute():
        path = ROOT / path
    credentials = Credentials.from_service_account_file(
        path, scopes=['https://www.googleapis.com/auth/spreadsheets.readonly'])
    url = 'https://sheets.googleapis.com/v4/spreadsheets/' + quote(setting('GOOGLE_SHEET_ID'), safe='') + '/values:batchGet'
    with AuthorizedSession(credentials) as session:
        response = session.get(url, params=[('ranges', r) for r in ranges] +
                               [('valueRenderOption', 'UNFORMATTED_VALUE')], timeout=30)
        if response.status_code != 200:
            raise ValueError(f'Sheets returned HTTP {response.status_code}; check API access, Viewer sharing and cell references.')
        entries = response.json().get('valueRanges', [])
    if len(entries) != 2:
        raise ValueError('Sheets did not return both team scores.')
    scores = []
    for entry in entries:
        values = entry.get('values', [])
        value = values[0][0] if values and values[0] else None
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise ValueError('Both score cells must contain numeric totals or formulas returning numbers.')
        try:
            number = Decimal(str(value))
        except InvalidOperation:
            raise ValueError('Invalid score value.') from None
        if not number.is_finite():
            raise ValueError('Score must be finite.')
        scores.append(number)
    return scores


def score_text(values, ranked=False):
    teams = list(zip(['Team 1', 'Team 2'], values))
    if ranked:
        teams.sort(key=lambda item: item[1], reverse=True)
    lines = ['**F2S Team Leaderboard**' if ranked else '**F2S Team Scores**']
    lines.extend(f'{name}: **{score:,}**' for name, score in teams)
    if ranked and values[0] == values[1]:
        lines.append('Teams are tied!')
    return '\n'.join(lines)


class BountyBot(commands.Bot):
    def __init__(self):
        intents = discord.Intents.default()
        intents.message_content = True
        super().__init__(command_prefix='!', intents=intents, allowed_mentions=discord.AllowedMentions.none())
        self.bounty_channel = int(setting('BOUNTY_CHANNEL_ID'))
        self.score_channel = int(setting('SCOREBOARD_CHANNEL_ID'))
        self.daily_channel = int(os.getenv('DAILY_CHANNEL_ID') or self.bounty_channel)
        self.bonus_channel = int(os.getenv('BONUS_CHANNEL_ID') or self.bounty_channel)
        load_pool('bounties.json')
        load_pool('daily_bounties.json')
        load_pool('bonus_bounties.json')
        next_uk_midnight()  # Check timezone data before connecting.
        self.state_path = ROOT / 'state.json'
        self.state = json.loads(self.state_path.read_text()) if self.state_path.exists() else {}
        self.roll_lock = asyncio.Lock()
        self.score_lock = asyncio.Lock()

    async def setup_hook(self):
        self.ensure_bounty_history()
        backup_current_files()
        self.scheduler.start()

    async def close(self):
        self.scheduler.cancel()
        await super().close()

    def save(self):
        backup_current_files()
        temporary = self.state_path.with_suffix('.tmp')
        temporary.write_text(json.dumps(self.state), encoding='utf-8')
        temporary.replace(self.state_path)
        backup_current_files()

    def ensure_bounty_history(self):
        changed = False
        if 'bounty_history' not in self.state:
            self.state['bounty_history'] = []
            if self.state.get('bounty'):
                self.state['bounty_history'].append({
                    'bounty': self.state['bounty'], 'selected_at': None})
            changed = True
        by_text = {entry['text']: entry['id'] for entry in load_bounties()}
        for entry in self.state['bounty_history']:
            if not entry.get('bounty_id'):
                entry['bounty_id'] = by_text.get(entry['bounty']) or bounty_record(entry['bounty'])['id']
                changed = True
        if self.state.get('bounty') and not self.state.get('bounty_id'):
            matching = next((entry['bounty_id'] for entry in reversed(self.state['bounty_history'])
                             if entry['bounty'] == self.state['bounty']), None)
            self.state['bounty_id'] = matching or by_text.get(self.state['bounty']) or bounty_record(self.state['bounty'])['id']
            changed = True
        if changed:
            self.save()
        return self.state['bounty_history']

    async def channel(self, channel_id):
        return self.get_channel(channel_id) or await self.fetch_channel(channel_id)

    async def roll_if_due(self):
        async with self.roll_lock:
            if self.state.get('posting_paused'):
                return False
            if self.state.get('next_at', 0) > time.time():
                return False
            history = self.ensure_bounty_history()
            pool = load_bounties()
            used = {entry['bounty_id'] for entry in history}
            if self.state.get('bounty_id'):
                used.add(self.state['bounty_id'])
            options = [entry for entry in pool if entry['id'] not in used]
            if not options:
                if not self.state.get('bounty_exhausted'):
                    channel = await self.channel(self.bounty_channel)
                    await channel.send(
                        '**No unused regular bounties remain.**\n'
                        'Add new entries to bounties.json to resume automatic posting. '
                        'Previously used bounties will not be reused.',
                        allowed_mentions=discord.AllowedMentions.none())
                    self.state['bounty_exhausted'] = True
                    self.save()
                    return True
                return False
            chosen = bounty_record(random.choice(options))
            bounty = chosen['text']
            next_at = int(time.time()) + INTERVAL
            channel = await self.channel(self.bounty_channel)
            await channel.send(
                f'@everyone\n**F2S Bounty {chosen["id"]} — 6-hour challenge** • ⏳ Ends <t:{next_at}:R>\n{bounty}\nDeadline: <t:{next_at}:F>\nSubmit evidence using your server’s usual process.',
                allowed_mentions=ANNOUNCEMENT_MENTIONS)
            self.state.update(bounty=bounty, bounty_id=chosen['id'], next_at=next_at, bounty_stopped=False,
                              bounty_exhausted=False)
            history.append({'bounty': bounty, 'bounty_id': chosen['id'], 'selected_at': next_at - INTERVAL})
            self.save()
            return True

    async def daily_if_due(self):
        async with self.roll_lock:
            if self.state.get('posting_paused'):
                return False
            if self.state.get('daily_next_at', 0) > time.time():
                return False
            pool = load_pool('daily_bounties.json')
            options = [x for x in pool if x != self.state.get('daily')] or pool
            challenge = random.choice(options)
            next_at = next_uk_midnight()
            channel = await self.channel(self.daily_channel)
            await channel.send(
                f'@everyone\n**F2S Daily Challenge**\n{challenge}\n'
                f'Ends at midnight UK time: <t:{next_at}:F> (<t:{next_at}:R>)\n'
                'Team-wide challenge. Only drops earned during this window count. '
                'Submit timestamped evidence; scoring is manual.',
                allowed_mentions=ANNOUNCEMENT_MENTIONS)
            self.state.update(daily=challenge, daily_next_at=next_at)
            self.save()
            return True

    async def select_bonus(self, replace=False):
        async with self.roll_lock:
            if self.state.get('posting_paused'):
                return False
            if self.state.get('bonus') and not replace:
                return False
            pool = load_pool('bonus_bounties.json')
            options = [x for x in pool if x != self.state.get('bonus')] or pool
            challenge = random.choice(options)
            channel = await self.channel(self.bonus_channel)
            await channel.send(
                f'@everyone\n**F2S Bonus Challenge — no time limit**\n{challenge}\n'
                'Only new drops earned while this bonus is active count. '
                'Submit timestamped evidence; scoring is manual.',
                allowed_mentions=ANNOUNCEMENT_MENTIONS)
            self.state.update(bonus=challenge, bonus_started_at=int(time.time()))
            self.save()
            return True

    async def scores_if_due(self):
        async with self.score_lock:
            async with self.roll_lock:
                if self.state.get('posting_paused'):
                    return False
                if 'score_next_at' not in self.state:
                    self.state['score_next_at'] = int(time.time()) + SCORE_INTERVAL
                    self.save()
                    return False
                if self.state['score_next_at'] > time.time():
                    return False
            values = await asyncio.to_thread(read_scores)
            async with self.roll_lock:
                # !pause may arrive while Google Sheets is being read.
                if self.state.get('posting_paused') or self.state['score_next_at'] > time.time():
                    return False
                posted_at = int(time.time())
                next_at = posted_at + SCORE_INTERVAL
                channel = await self.channel(self.score_channel)
                await channel.send(
                    score_text(values) + f'\nAutomatic 12-hour update • <t:{posted_at}:f>'
                    f'\nNext automatic update: <t:{next_at}:f>',
                    allowed_mentions=discord.AllowedMentions.none())
                self.state.update(score_next_at=next_at, score_last_posted_at=posted_at)
                self.save()
                return True

    @tasks.loop(seconds=30)
    async def scheduler(self):
        backup_current_files()  # Also preserve valid list edits while event posting is paused.
        if self.state.get('posting_paused'):
            return
        for label, action in [('bounty', self.roll_if_due), ('daily', self.daily_if_due),
                              ('scores', self.scores_if_due)]:
            try:
                await action()
            except Exception as exc:
                # Avoid logging credential-bearing exception contents.
                LOG.error('Scheduled %s failed (%s); retrying in 30 seconds.', label, type(exc).__name__)

    @scheduler.before_loop
    async def before_scheduler(self):
        await self.wait_until_ready()


def build_bot():
    bot = BountyBot()

    @bot.event
    async def on_ready():
        LOG.info('F2S Bounty Bot connected. Challenge posting is %s.',
                 'PAUSED; use !resume to enable it' if bot.state.get('posting_paused') else 'ACTIVE')

    async def paused_reply(ctx):
        if bot.state.get('posting_paused'):
            await ctx.send('**Challenge posting is paused.** You can edit the lists and view history or scores. A server manager can use !resume in the bounty channel.')
            return True
        return False

    async def event_channel(ctx, command):
        if ctx.channel.id not in {bot.bounty_channel, bot.daily_channel,
                                  bot.bonus_channel, bot.score_channel}:
            await ctx.send(f'Use !{command} in <#{bot.bounty_channel}> or another event channel.')
            return False
        return True

    @bot.command(name='checklists', help='Check all challenge files for formatting errors without selecting anything.')
    @commands.guild_only()
    @commands.cooldown(1, 15, commands.BucketType.channel)
    async def checklists(ctx):
        if not await event_channel(ctx, 'checklists'):
            return
        lines = ['**Challenge file checks**']
        for item in checklist_report():
            symbol = '✓' if item['ok'] else '✗'
            lines.append(f'{symbol} **{item["name"]}** — {item["message"]}')
        await ctx.send('\n'.join(lines), allowed_mentions=discord.AllowedMentions.none())

    @bot.command(name='status', help='Show pause state, upcoming posts, remaining bounties, backups and Sheets connectivity.')
    @commands.guild_only()
    @commands.cooldown(1, 30, commands.BucketType.channel)
    async def status(ctx):
        if not await event_channel(ctx, 'status'):
            return
        paused = bool(bot.state.get('posting_paused'))
        lines = ['**F2S Bounty Bot status**',
                 'Posting: **PAUSED**' if paused else 'Posting: **ACTIVE**']
        try:
            pool = load_bounties()
            used = {item.get('bounty_id') for item in bot.state.get('bounty_history', [])}
            if bot.state.get('bounty_id'):
                used.add(bot.state['bounty_id'])
            remaining = sum(item['id'] not in used for item in pool)
            lines.append(f'Regular bounties: **{remaining} unused / {len(pool)} total**')
        except (OSError, ValueError, UnicodeError):
            lines.append('Regular bounty list: **needs attention — use !checklists**')
        for label, key in [('Regular bounty', 'next_at'), ('Daily challenge', 'daily_next_at'),
                           ('Score update', 'score_next_at')]:
            deadline = bot.state.get(key)
            display = f'<t:{int(deadline)}:F>' if isinstance(deadline, (int, float)) else 'Not scheduled yet'
            lines.append(f'{label} saved deadline: {display}')
        if paused:
            lines.append('Automatic posts are suspended; overdue deadlines run after !resume.')
        try:
            await asyncio.wait_for(asyncio.to_thread(read_scores), timeout=10)
            lines.append('Google Sheets: **reachable (read-only)**')
        except Exception as exc:
            LOG.warning('Status Sheets check failed (%s).', type(exc).__name__)
            lines.append('Google Sheets: **unavailable — try check_scores.cmd locally**')
        backups = backup_current_files()
        lines.append('Local backups: **need attention**' if backups['skipped']
                     else 'Local backups: **up to date**')
        lines.append('Bonus selection: **restricted to your configured account**')
        await ctx.send('\n'.join(lines), allowed_mentions=discord.AllowedMentions.none())

    @bot.command(name='pause', help='Pause all challenge posts without shutting down the bot. Requires Manage Server.')
    @commands.guild_only()
    @commands.has_guild_permissions(manage_guild=True)
    async def pause(ctx):
        if ctx.channel.id != bot.bounty_channel:
            await ctx.send(f'Use !pause in <#{bot.bounty_channel}>.')
            return
        async with bot.roll_lock:
            bot.state['posting_paused'] = True
            bot.save()
        await ctx.send('**All automatic event posts and challenge selections are paused.** The bot remains online for lists, history and manual scores. Edit your files normally. Use !resume when ready; overdue event posts will then run.')

    @bot.command(name='resume', help='Resume challenge posts; overdue challenges post on the next scheduler check. Requires Manage Server.')
    @commands.guild_only()
    @commands.has_guild_permissions(manage_guild=True)
    async def resume(ctx):
        if ctx.channel.id != bot.bounty_channel:
            await ctx.send(f'Use !resume in <#{bot.bounty_channel}>.')
            return
        async with bot.roll_lock:
            bot.state['posting_paused'] = False
            if 'score_next_at' not in bot.state:
                bot.state['score_next_at'] = int(time.time()) + SCORE_INTERVAL
            bot.save()
        await ctx.send('**Event posting is active.** Overdue regular/daily challenges and any overdue score update will post within about 30 seconds. The first automatic score update is 12 hours after enabling its schedule. Bonus challenges still require !bonus.')

    @bot.command(name='bounty')
    @commands.guild_only()
    @commands.cooldown(1, 10, commands.BucketType.channel)
    async def bounty(ctx):
        if ctx.channel.id != bot.bounty_channel:
            await ctx.send(f'Use !bounty in <#{bot.bounty_channel}>.')
            return
        if await bot.roll_if_due():
            return  # The rollover announcement is already this command's response.
        if await paused_reply(ctx):
            return
        if bot.state.get('bounty_exhausted'):
            await ctx.send('**No unused regular bounties remain.** Add new entries to bounties.json; used bounties will not be repeated.')
            return
        if bot.state.get('bounty_stopped'):
            await ctx.send(
                '**No active bounty — the previous bounty was stopped.**\n'
                f'Next automatic bounty: <t:{bot.state["next_at"]}:F> '
                f'(<t:{bot.state["next_at"]}:R>)')
            return
        await ctx.send(
            f'**Current F2S bounty {bot.state.get("bounty_id", "")}** • ⏳ Ends <t:{bot.state["next_at"]}:R>\n'
            f'{bot.state["bounty"]}\nDeadline: <t:{bot.state["next_at"]}:F>')

    @bot.command(name='bountystop', help='End the current bounty early; the next scheduled bounty still posts. Requires Manage Server.')
    @commands.guild_only()
    @commands.has_guild_permissions(manage_guild=True)
    async def bountystop(ctx):
        if ctx.channel.id != bot.bounty_channel:
            await ctx.send(f'Use !bountystop in <#{bot.bounty_channel}>.')
            return
        async with bot.roll_lock:
            if (not bot.state.get('bounty') or bot.state.get('bounty_stopped')
                    or bot.state.get('next_at', 0) <= time.time()):
                await ctx.send('There is no active bounty to stop. The automatic schedule is still running.')
                return
            bot.state['bounty_stopped'] = True
            bot.save()
            await ctx.send(
                '**The current bounty has been stopped.**\n'
                f'Next automatic bounty: <t:{bot.state["next_at"]}:F> '
                f'(<t:{bot.state["next_at"]}:R>)')

    async def show_bounty_page(ctx, title, lines, page):
        pages = text_pages(lines or ['No entries recorded yet.'])
        if not 1 <= page <= len(pages):
            await ctx.send(f'Choose a page from 1 to {len(pages)}.')
            return
        await ctx.send(f'**{title} — page {page}/{len(pages)}**\n{pages[page-1]}',
                       allowed_mentions=discord.AllowedMentions.none())

    @bot.command(name='bountylist', help='List regular bounties with current/used/not-recorded labels. Optional page number.')
    @commands.guild_only()
    @commands.cooldown(1, 5, commands.BucketType.channel)
    async def bountylist(ctx, page: int = 1):
        if ctx.channel.id != bot.bounty_channel:
            await ctx.send(f'Use !bountylist in <#{bot.bounty_channel}>.')
            return
        pool = load_bounties()
        history = bot.ensure_bounty_history()
        used = {entry['bounty_id'] for entry in history}
        active = (bot.state.get('bounty_id') if not bot.state.get('bounty_stopped')
                  and bot.state.get('next_at', 0) > time.time() else None)
        lines = ['Labels cover tracked selections only; older usage is unknown. '
                 'CURRENT and USED entries are excluded from future selections.']
        for entry in pool:
            label = 'CURRENT' if entry['id'] == active else 'USED' if entry['id'] in used else 'NOT RECORDED'
            lines.append(f'**{entry["id"]} [{label}]** {entry["text"]}')
        await show_bounty_page(ctx, 'Regular bounty list', lines, page)

    @bot.command(name='bountyhistory', help='Show recorded regular bounty selections, newest first. Optional page number.')
    @commands.guild_only()
    @commands.cooldown(1, 5, commands.BucketType.channel)
    async def bountyhistory(ctx, page: int = 1):
        if ctx.channel.id != bot.bounty_channel:
            await ctx.send(f'Use !bountyhistory in <#{bot.bounty_channel}>.')
            return
        history = bot.ensure_bounty_history()
        lines = ['Tracking starts with the bounty present when this feature was enabled; '
                 'earlier selections were not recorded.']
        for entry in reversed(history):
            when = (f'<t:{entry["selected_at"]}:f>' if entry['selected_at'] is not None
                    else 'Present when tracking began; selection time unknown')
            lines.append(f'• **{entry["bounty_id"]} — {when}** — {entry["bounty"]}')
        if not history:
            lines.append('No bounty selections recorded yet.')
        await show_bounty_page(ctx, 'Regular bounty history', lines, page)

    @bot.command(name='daily')
    @commands.guild_only()
    @commands.cooldown(1, 10, commands.BucketType.channel)
    async def daily(ctx):
        if ctx.channel.id != bot.daily_channel:
            await ctx.send(f'Use !daily in <#{bot.daily_channel}>.')
            return
        if await bot.daily_if_due():
            return
        if await paused_reply(ctx):
            return
        await ctx.send(
            f'**Current F2S Daily Challenge**\n{bot.state["daily"]}\n'
            f'Ends at midnight UK time: <t:{bot.state["daily_next_at"]}:F> '
            f'(<t:{bot.state["daily_next_at"]}:R>)\n'
            'Team-wide. Only drops earned during this window count; submit timestamped evidence.')

    @bot.command(name='bonus', help='Select and post a new random bonus challenge. No timer or completion command.')
    @commands.guild_only()
    @commands.check(bonus_owner_check)
    async def bonus(ctx):
        if ctx.channel.id != bot.bonus_channel:
            await ctx.send(f'Use !bonus in <#{bot.bonus_channel}>.')
            return
        if not await bot.select_bonus(replace=True):
            await paused_reply(ctx)

    async def display_scores(ctx, ranked=False):
        if ctx.channel.id != bot.score_channel:
            await ctx.send(f'Use this command in <#{bot.score_channel}>.')
            return
        async with ctx.typing():
            try:
                values = await asyncio.to_thread(read_scores)
            except Exception as exc:
                LOG.warning('Score lookup failed (%s).', type(exc).__name__)
                await ctx.send('Scores unavailable. Check the local score configuration with check_scores.cmd.')
                return
        await ctx.send(score_text(values, ranked))

    @bot.command(name='score')
    @commands.guild_only()
    @commands.cooldown(1, 15, commands.BucketType.channel)
    async def score(ctx):
        await display_scores(ctx)

    @bot.command(name='leaderboard')
    @commands.guild_only()
    @commands.cooldown(1, 15, commands.BucketType.channel)
    async def leaderboard(ctx):
        await display_scores(ctx, ranked=True)

    @bot.event
    async def on_command_error(ctx, error):
        if isinstance(error, commands.CommandNotFound):
            return
        if isinstance(error, commands.CommandOnCooldown):
            await ctx.send(f'Please wait {error.retry_after:.0f} seconds.')
            return
        if isinstance(error, commands.NoPrivateMessage):
            return
        if isinstance(error, BonusOwnerOnly):
            await ctx.send('Only the configured bonus owner can use !bonus.')
            return
        if isinstance(error, commands.MissingPermissions):
            await ctx.send('You need Manage Server permission to use this management command.')
            return
        if isinstance(error, commands.BadArgument):
            await ctx.send('Use a whole-number page, for example !bountylist 2 or !bountyhistory 2.')
            return
        LOG.warning('Command failed (%s).', type(error).__name__)
        await ctx.send('Command failed. Check bot permissions and local configuration.')

    return bot


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check-scores', action='store_true')
    parser.add_argument('--check-lists', action='store_true')
    args = parser.parse_args()
    if args.check_lists:
        report = checklist_report()
        for item in report:
            print(f'{item["name"]}: {"OK" if item["ok"] else "ERROR"} - {item["message"]}')
        raise SystemExit(0 if all(item['ok'] for item in report) else 1)
    if args.check_scores:
        try:
            scores = read_scores()
            print(f'Team 1: {scores[0]:,}\nTeam 2: {scores[1]:,}\nRead-only score check passed.')
        except ValueError as exc:
            print(str(exc))
            raise SystemExit(1)
        except Exception as exc:
            print(f'Score check failed ({type(exc).__name__}). Check credentials, Sheet sharing and API access.')
            raise SystemExit(1)
        return
    # Windows byte-range lock prevents duplicate copies posting bounties.
    import msvcrt
    with (ROOT / 'bot.lock').open('a+b') as lock:
        lock.seek(0)
        lock.write(b'0')
        lock.flush()
        lock.seek(0)
        try:
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError:
            raise SystemExit('Another copy of this bot is already running.')
        try:
            bot = build_bot()
            from runelite_feed import install
            install(bot, ROOT, read_scores)
            from clan_sync import install as install_clan_sync
            install_clan_sync(bot, ROOT)
            bot.run(setting('DISCORD_TOKEN'), log_handler=None)
        except Exception as exc:
            print(f'Bot stopped ({type(exc).__name__}). Check .env, Discord token, intents and permissions.')
            raise SystemExit(1)


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
    main()
