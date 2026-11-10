import type { SceneId } from '../state/store';

/**
 * Full-screen background: a big window looking out over hills at sunset.
 * Pure CSS/SVG, so no image download is needed. To use a photo instead,
 * put it in src/assets and set `background-image` on .scene__photo in global.css.
 *
 * mode="login" → blurred, zoomed and dimmed. mode="app" → sharp.
 * The CSS transition between the two is the "scene change" after login.
 */
export function Scene({ mode, variant }: { mode: 'login' | 'app'; variant: SceneId }) {
  return (
    <div className={`scene scene--${mode} scene--${variant}`} aria-hidden>
      <div className="scene__inner">
        <div className="scene__sky" />
        <div className="scene__stars" />
        <div className="scene__sun" />
        <svg className="scene__hills" viewBox="0 0 1600 500" preserveAspectRatio="none">
          <path className="h1" d="M0 230 C140 170 260 210 380 160 S620 90 760 150 S1000 210 1140 140 S1420 80 1600 150 V500 H0Z" />
          <path className="h2" d="M0 300 C160 250 300 290 460 240 S760 200 900 260 S1180 300 1320 240 S1500 220 1600 250 V500 H0Z" />
          <path className="h3" d="M0 370 C200 330 380 360 560 330 S880 320 1040 350 S1340 370 1600 330 V500 H0Z" />
        </svg>
        <div className="scene__lights" />
        <div className="scene__haze" />
        <div className="scene__photo" />
        <div className="scene__frame">
          <i style={{ left: '7%' }} /><i style={{ left: '33%' }} /><i style={{ left: '59%' }} /><i style={{ left: '85%' }} />
          <b className="top" /><b className="sill" />
        </div>
        <div className="scene__floor" />
        <div className="scene__vignette" />
      </div>
    </div>
  );
}
