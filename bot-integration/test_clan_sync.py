import asyncio
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace as NS
import unittest
from unittest.mock import AsyncMock, patch
from aiohttp.test_utils import TestClient, TestServer
from clan_sync import ActivityStore, role_plan, install, RETENTION, FRESHNESS

NOW = 1_000_000


def snapshot(available=True, members=None, collector='collector-000000000001'):
    return {'clan':'FreeToScape', 'collector':collector, 'available':available,
            'members':members if members is not None else [{'name':'Alice','present':True,'rank':'Topaz'}]}


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.store = ActivityStore(':memory:')
        self.store.link('Alice','123456')
    def tearDown(self):
        self.store.close()
    def test_only_officer_linked_accounts_are_retained(self):
        data = snapshot(members=[{'name':'Alice','present':True,'rank':'Topaz'}, {'name':'Bob','present':True,'rank':'Owner'}])
        self.store.snapshot(data,'FreeToScape',NOW)
        self.assertEqual(len(self.store.observations(NOW)),1)
        self.assertEqual(self.store.observations(NOW)[0][0],'alice')
        self.assertEqual(self.store.db.execute('SELECT COUNT(*) FROM history WHERE rsn="bob"').fetchone()[0],0)
    def test_one_to_one_links_require_explicit_unlink(self):
        with self.assertRaises(ValueError): self.store.link('Bob','123456')
        with self.assertRaises(ValueError): self.store.link('Alice','654321')
        self.store.unlink('123456')
        self.store.link('Bob','123456')
    def test_stale_reporter_becomes_unknown_not_offline(self):
        self.store.snapshot(snapshot(),'FreeToScape',NOW)
        self.assertEqual(self.store.observations(NOW+FRESHNESS+1),[])
        self.assertEqual(self.store.db.execute('SELECT presence FROM current').fetchone()[0],'unknown')
    def test_unavailable_or_missing_roster_does_not_produce_a_rank(self):
        self.store.snapshot(snapshot(),'FreeToScape',NOW)
        self.store.snapshot(snapshot(False),'FreeToScape',NOW+1)
        row = self.store.observations(NOW+1)[0]
        self.assertEqual(row[2],'unknown'); self.assertIsNone(row[3])
    def test_history_expires_and_unlink_cascades(self):
        self.store.snapshot(snapshot(),'FreeToScape',NOW-RETENTION-10)
        self.store.purge(NOW)
        self.assertEqual(self.store.db.execute('SELECT COUNT(*) FROM history WHERE at<?',(NOW-RETENTION,)).fetchone()[0],0)
        self.assertEqual(self.store.db.execute('SELECT COUNT(*) FROM current').fetchone()[0],0)
        self.store.unlink('123456')
        self.assertEqual(self.store.db.execute('SELECT COUNT(*) FROM history').fetchone()[0],0)
    def test_wrong_clan_extra_data_and_concurrent_reporter_are_rejected(self):
        with self.assertRaises(ValueError): self.store.snapshot(snapshot(),'AnotherClan',NOW)
        data=snapshot(); data['world']=301
        with self.assertRaises(ValueError): self.store.snapshot(data,'FreeToScape',NOW)
        self.store.snapshot(snapshot(),'FreeToScape',NOW)
        with self.assertRaises(ValueError): self.store.snapshot(snapshot(collector='collector-000000000002'),'FreeToScape',NOW+1)
        self.store.snapshot(snapshot(collector='collector-000000000002'),'FreeToScape',NOW+FRESHNESS+1)


class PlannerTests(unittest.TestCase):
    def setUp(self):
        self.mapping={'topaz':'100','sapphire':'200'}
        self.roles={str(i):{'managed':False,'permissions':0,'position':i//10} for i in [100,200,999]}
        self.rows=[('alice','123456','observed_online','Topaz',NOW)]
    def test_only_mapped_rank_roles_are_changed(self):
        plan=role_plan(self.rows,self.mapping,{'123456':['200','999']},self.roles,100,True)[0]
        self.assertEqual(plan['add'],['100']); self.assertEqual(plan['remove'],['200'])
        self.assertNotIn('999',plan['remove']); self.assertFalse(plan['blocked'])
    def test_permissions_hierarchy_and_privileged_role_guard(self):
        self.assertTrue(role_plan(self.rows,self.mapping,{'123456':[]},self.roles,5,True)[0]['blocked'])
        self.assertTrue(role_plan(self.rows,self.mapping,{'123456':[]},self.roles,100,False)[0]['blocked'])
        self.roles['100']['permissions']=8
        self.assertEqual(role_plan(self.rows,self.mapping,{'123456':[]},self.roles,100,True),[])
    def test_same_rank_missing_rank_and_unmapped_rank_do_nothing(self):
        self.assertEqual(role_plan(self.rows,self.mapping,{'123456':['100']},self.roles,100,True),[])
        self.assertEqual(role_plan([('alice','123456','unknown',None,NOW)],self.mapping,{'123456':['200']},self.roles,100,True),[])
        self.assertEqual(role_plan([('alice','123456','observed_online','Owner',NOW)],self.mapping,{'123456':['200']},self.roles,100,True),[])


class ReceiverTests(unittest.IsolatedAsyncioTestCase):
    async def test_authentication_and_dry_run_receiver(self):
        with tempfile.TemporaryDirectory() as root:
            Path(root,'clan_sync_roles.json').write_text(json.dumps({'guild_id':'1236133761859584010','clan':'FreeToScape','rank_roles':{'topaz':'100','sapphire':'200'}}))
            commands={}
            def command(**options):
                def register(function): commands[options['name']]=function; return function
                return register
            async def wait_ready(): await asyncio.Event().wait()
            bot=NS(setup_hook=AsyncMock(),close=AsyncMock(),command=command,wait_until_ready=wait_ready)
            apps=[]
            def runner(app,**options):
                apps.append(app); return NS(setup=AsyncMock(),cleanup=AsyncMock())
            with patch.dict('os.environ',{'CLAN_SYNC_ENABLED':'true','CLAN_SYNC_REPORTER_TOKEN':'x'*32},clear=True), \
                    patch('clan_sync.web.AppRunner',side_effect=runner), \
                    patch('clan_sync.web.TCPSite',return_value=NS(start=AsyncMock())):
                install(bot,root)
                await bot.setup_hook()
                client=TestClient(TestServer(apps[0]))
                await client.start_server()
                try:
                    self.assertEqual((await client.get('/v1/clan/links')).status,401)
                    headers={'Authorization':'Bearer '+'x'*32}
                    response=await client.get('/v1/clan/links',headers=headers)
                    self.assertEqual(response.status,200)
                    self.assertEqual((await response.json())['members'],[])
                    response=await client.post('/v1/clan/snapshot',json=snapshot(),headers=headers)
                    self.assertEqual(response.status,200)
                    self.assertEqual((await response.json())['mode'],'dry-run')
                    bad=snapshot(); bad['clan']='Other'
                    self.assertEqual((await client.post('/v1/clan/snapshot',json=bad,headers=headers)).status,400)
                    roles=[NS(id=i,managed=False,permissions=NS(value=0),position=i//10) for i in [100,200,999]]
                    member=NS(roles=roles[1:],add_roles=AsyncMock(),remove_roles=AsyncMock())
                    guild=NS(roles=roles,fetch_member=AsyncMock(return_value=member),
                        me=NS(top_role=NS(position=100),guild_permissions=NS(manage_roles=True)))
                    bot.get_guild=lambda guild_id:guild
                    ctx=NS(guild=guild,send=AsyncMock())
                    await commands['clanlink'](ctx,'123456',rsn='Alice')
                    await client.post('/v1/clan/snapshot',json=snapshot(),headers=headers)
                    with patch.dict('os.environ',{'CLAN_SYNC_APPLY_ROLES':'true'}):
                        await commands['clanrankpreview'](ctx)
                    member.add_roles.assert_not_awaited()
                    member.remove_roles.assert_not_awaited()
                    self.assertIn('Dry-run only',ctx.send.call_args.args[0])
                finally:
                    await client.close(); await bot.close()


if __name__=='__main__': unittest.main()
