import { useIsMobile } from '../../hooks/useMediaQuery';
import { useStore, type PageId } from '../../state/store';
import { Toasts } from '../ui';
import { DesktopNav } from './DesktopNav';
import { MobileSidebar } from './MobileSidebar';
import { HomePage } from '../../pages/HomePage';
import { AttendancePage } from '../../pages/AttendancePage';
import { PayrollPage } from '../../pages/PayrollPage';
import { ApprovalsPage } from '../../pages/ApprovalsPage';
import { MessagesPage } from '../../pages/MessagesPage';
import { PeoplePage } from '../../pages/PeoplePage';
import { OrgPage } from '../../pages/OrgPage';
import { SettingsPage } from '../../pages/SettingsPage';
import { CodesPage } from '../../pages/CodesPage';
import { PrivacyLogsPage } from '../../pages/PrivacyLogsPage';
import { useEffect, useState, type JSX } from 'react';

const PAGES: Record<PageId, () => JSX.Element> = {
  home: HomePage,
  attendance: AttendancePage,
  payroll: PayrollPage,
  approvals: ApprovalsPage,
  messages: MessagesPage,
  people: PeoplePage,
  org: OrgPage,
  settings: SettingsPage,
  codes: CodesPage,
  privacyLogs: PrivacyLogsPage,
};

/** Desktop: top pill bar + floating dock (video 2). Mobile: glass sidebar (video 3). */
export function AppShell() {
  const { page } = useStore();
  const mobile = useIsMobile();
  const Page = PAGES[page];
  // right after login everything waits for the scene change before rising in
  const [entering, setEntering] = useState(true);
  useEffect(() => { const t = setTimeout(() => setEntering(false), 2200); return () => clearTimeout(t); }, []);
  return (
    <div className={`shell ${mobile ? 'shell--mobile' : 'shell--desktop'}${entering ? ' entering' : ''}`}>
      {mobile ? <MobileSidebar /> : <DesktopNav />}
      {/* key → remount on page change, which replays the staggered rise animation */}
      <main key={page} className="page">
        <Page />
      </main>
      <Toasts />
    </div>
  );
}
