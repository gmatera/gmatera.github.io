"""Generates index.html (brand home and app catalog) from apps.json.

Usage: python build.py
To add an app, add an entry to apps.json and run the script again.
status: "available" (on the Marketplace), "review" (in Marketplace review), "development".
"""
import html
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
STATUS = {
    'available': ('Available on the Atlassian Marketplace', 'ok'),
    'review': ('In Atlassian Marketplace review', 'wait'),
    'development': ('In development', 'dev'),
}

MARK = ('<svg class="mark" viewBox="0 0 64 64" aria-hidden="true"><rect width="64" height="64" rx="15" fill="var(--brand)"/>'
        '<path d="M21 14v36M43 14v36" stroke="#fff" stroke-width="6" stroke-linecap="round"/>'
        '<path d="M27 22h10M27 32h10M27 42h10" stroke="#9fd8ff" stroke-width="4" stroke-linecap="round"/></svg>')

STYLE = """
:root {
  --brand: #0f4c9c; --brand-ink: #0b3570; --accent: #1f7ae0;
  --bg: #f6f8fb; --card: #ffffff; --ink: #13233d; --muted: #5a6b85; --line: #e1e7f0;
  --ok-bg: #e3f6ea; --ok-ink: #17663a; --wait-bg: #fff4dc; --wait-ink: #7a5300; --dev-bg: #eceff4; --dev-ink: #4a5568;
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --brand: #2b6fd1; --brand-ink: #9cc3ff; --accent: #6aa8ff;
    --bg: #0d1420; --card: #152033; --ink: #e8eef8; --muted: #9db0cc; --line: #23324a;
    --ok-bg: #133a25; --ok-ink: #8fe0ad; --wait-bg: #3a2f12; --wait-ink: #f3cf7a; --dev-bg: #222c3c; --dev-ink: #b6c2d4;
  }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--ink);
  font: 17px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
a { color: var(--accent); }
.wrap { max-width: 1040px; margin: 0 auto; padding: 0 20px; }
header.top { padding: 22px 0; }
header.top .wrap { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
nav.top a { margin-left: 20px; color: var(--ink); text-decoration: none; font-weight: 600; font-size: 16px; }
nav.top a:hover { color: var(--accent); }
.brand { display: flex; align-items: center; gap: 12px; text-decoration: none; color: var(--ink);
  font-weight: 700; font-size: 22px; letter-spacing: -0.01em; }
.mark { width: 38px; height: 38px; flex: none; }
.hero { padding: 48px 0 40px; }
.hero h1 { font-size: clamp(32px, 5vw, 50px); line-height: 1.12; margin: 0 0 18px; letter-spacing: -0.02em; max-width: 820px; }
.hero p { font-size: 20px; color: var(--muted); max-width: 720px; margin: 0; }
h2 { font-size: 28px; margin: 0 0 22px; letter-spacing: -0.01em; }
section { padding: 34px 0; }
.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 22px; }
.card { background: var(--card); border: 1px solid var(--line); border-radius: 16px; padding: 26px; display: flex; flex-direction: column; }
.card-head { display: flex; gap: 16px; align-items: center; margin-bottom: 14px; }
.icon { width: 56px; height: 56px; border-radius: 13px; flex: none; object-fit: cover; }
.icon.placeholder { background: var(--dev-bg); display: grid; place-items: center; color: var(--dev-ink); font-weight: 700; font-size: 22px; }
.card h3 { margin: 0; font-size: 20px; line-height: 1.3; }
.badge { display: inline-block; margin-top: 6px; font-size: 13px; font-weight: 600; padding: 2px 10px; border-radius: 999px; }
.badge.ok { background: var(--ok-bg); color: var(--ok-ink); }
.badge.wait { background: var(--wait-bg); color: var(--wait-ink); }
.badge.dev { background: var(--dev-bg); color: var(--dev-ink); }
.card p { margin: 0 0 12px; }
.card ul { margin: 0 0 16px; padding-left: 20px; color: var(--muted); }
.card li { margin-bottom: 6px; }
.price { font-weight: 600; margin-bottom: 14px; }
.links { margin-top: auto; display: flex; flex-wrap: wrap; gap: 10px; }
.links a { text-decoration: none; border: 1px solid var(--line); border-radius: 10px; padding: 7px 14px; font-weight: 600; font-size: 15px; }
.links a:first-child { background: var(--brand); border-color: var(--brand); color: #fff; }
.principles { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 22px; }
.principles h3 { margin: 0 0 6px; font-size: 18px; }
.principles p { margin: 0; color: var(--muted); }
.contact { background: var(--card); border: 1px solid var(--line); border-radius: 16px; padding: 26px; }
.contact p { margin: 6px 0; }
.doc h2 { margin-top: 10px; }
.doc ul { padding-left: 22px; }
.doc li { margin-bottom: 6px; }
.doc .card { margin-bottom: 18px; }
.doc .card h3 { margin-bottom: 8px; }
footer { padding: 30px 0 46px; color: var(--muted); font-size: 14px; border-top: 1px solid var(--line); margin-top: 26px; }
footer p { margin: 4px 0; }
"""


def esc(text):
    return html.escape(text, quote=True)


def card(app):
    label, kind = STATUS[app['status']]
    if app.get('icon'):
        icon = f'<img class="icon" src="{esc(app["icon"])}" alt="">'
    else:
        icon = f'<div class="icon placeholder" aria-hidden="true">{esc(app["name"][0])}</div>'
    points = ''.join(f'<li>{esc(p)}</li>' for p in app.get('points', []))
    price = f'<div class="price">{esc(app["pricing"])}</div>' if app.get('pricing') else ''
    links = ''.join(f'<a href="{esc(link["href"])}">{esc(link["label"])}</a>' for link in app.get('links', []))
    return f"""
      <article class="card">
        <div class="card-head">{icon}<div><h3>{esc(app['name'])}</h3><span class="badge {kind}">{esc(label)}</span></div></div>
        <p>{esc(app['summary'])}</p>
        <ul>{points}</ul>
        {price}
        <div class="links">{links}</div>
      </article>"""


def support_card(app):
    """Cosa allegare a una richiesta per una certa app (campo "support" di apps.json)."""
    support = app['support']
    tips = ''.join(f'<li>{esc(t)}</li>' for t in support.get('include', []))
    links = ''.join(f'<a href="{esc(link["href"])}">{esc(link["label"])}</a>' for link in support.get('links', []))
    note = f'<p><strong>{esc(support["note"])}</strong></p>' if support.get('note') else ''
    return f"""
      <article class="card" id="{esc(app['slug'])}">
        <h3>{esc(app['name'])}</h3>
        <p>Please also include:</p>
        <ul>{tips}</ul>
        {note}
        <div class="links">{links}</div>
      </article>"""


def page(title, description, body):
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{esc(title)}</title>
<meta name="description" content="{esc(description)}">
<link rel="icon" href="favicon.svg" type="image/svg+xml">
<style>{STYLE}</style>
</head>
<body>
<header class="top"><div class="wrap"><a class="brand" href="./">{MARK}<span>Steadyseam</span></a>
  <nav class="top"><a href="./#apps">Apps</a><a href="support.html">Support</a></nav></div></header>
<main class="wrap">
{body}
</main>
<footer><div class="wrap">
  <p>Steadyseam is a brand of Sasso di Matera Giuseppe S.a.s. · Via Clefi 2, 20146 Milano, Italy · VAT IT13043470965 · <a href="support.html">Support</a> · <a href="privacy.html">Privacy</a></p>
  <p>Atlassian, Jira and Jira Service Management are trademarks of Atlassian. GitLab is a trademark of GitLab Inc. Subversion is a trademark of the Apache Software Foundation. Steadyseam is not affiliated with or endorsed by these companies.</p>
</div></footer>
</body>
</html>
"""


def main():
    with open(os.path.join(HERE, 'apps.json'), encoding='utf-8') as f:
        apps = json.load(f)
    cards = ''.join(card(app) for app in apps)
    body = f"""
<section class="hero">
  <h1>Dependable apps for Atlassian Cloud</h1>
  <p>Small, focused apps that close real gaps in Jira and Jira Service Management. Each one is tested
  until it does not break, documented in plain words, and supported directly by the person who built it.</p>
</section>

<section id="apps">
  <h2>Apps</h2>
  <div class="grid">{cards}
  </div>
</section>

<section>
  <h2>How we build</h2>
  <div class="principles">
    <div><h3>Reliability first</h3><p>Every release goes through extensive automated tests, failure scenarios
    and checks against the real products the app connects to.</p></div>
    <div><h3>Your data, kept close</h3><p>Where possible, apps run entirely on Atlassian's platform: no servers of
    ours, and data stays in Atlassian's cloud with your site's data residency.</p></div>
    <div><h3>Answers from the builder</h3><p>Questions and problems reach the developer directly, in English or
    Italian; we aim to answer within one business day.</p></div>
    <div><h3>Fair pricing</h3><p>Simple per-user pricing on the Atlassian Marketplace, and free plans for small
    teams whenever we can.</p></div>
  </div>
</section>

<section>
  <div class="contact">
    <h2>Contact</h2>
    <p>Support: <a href="mailto:support@steadyseam.com">support@steadyseam.com</a> (<a href="support.html">hours and what to include</a>)</p>
    <p>Partners and resellers: <a href="mailto:giuseppe@steadyseam.com">giuseppe@steadyseam.com</a></p>
  </div>
</section>
"""
    with open(os.path.join(HERE, 'index.html'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(page('Steadyseam: dependable apps for Atlassian Cloud',
                     'Steadyseam builds small, dependable apps for Jira and Jira Service Management on Atlassian Cloud.', body))
    privacy = """
<section class="hero">
  <h1>Privacy</h1>
  <p>This website does not use cookies, analytics or tracking of any kind.</p>
</section>
<section>
  <p>The site is hosted on GitHub Pages. GitHub may record technical data such as IP addresses to deliver and
  protect the site, as described in the <a href="https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement">GitHub Privacy Statement</a>.</p>
  <p>If you write to us, we use your name, e-mail address and message only to answer you, and keep them for up to
  24 months after the last exchange. You can ask us to access, correct or delete them at
  <a href="mailto:support@steadyseam.com">support@steadyseam.com</a>, and you can lodge a complaint with your data
  protection authority (in Italy, the Garante per la protezione dei dati personali).</p>
  <p>Each app has its own privacy policy, linked from its page, describing the data the app processes.</p>
  <p>Data controller: Sasso di Matera Giuseppe S.a.s., Via Clefi 2, 20146 Milano, Italy, VAT IT13043470965.</p>
</section>
"""
    with open(os.path.join(HERE, 'privacy.html'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(page('Privacy · Steadyseam', 'Privacy information for the Steadyseam website.', privacy))
    app_cards = ''.join(support_card(app) for app in apps if app.get('support'))
    support = f"""
<section class="hero">
  <h1>Support</h1>
  <p>Questions and problems with any Steadyseam app reach the developer who built it, in English or Italian.</p>
</section>
<section class="doc">
  <div class="card">
    <h2>Contact</h2>
    <p>E-mail: <a href="mailto:support@steadyseam.com">support@steadyseam.com</a></p>
    <p>Hours: Monday to Friday, 9:00-18:00 Central European Time (Europe/Rome), except Italian public holidays.</p>
    <p>Response: first answer within one business day. Problems that stop an app from working for everyone come first.</p>
  </div>
  <h2>What to include</h2>
  <ul>
    <li>the name of the app and the address of your site (for example <code>your-company.atlassian.net</code>);</li>
    <li>what you expected and what happened instead, with screenshots if you can.</li>
  </ul>
  <p><strong>Never send passwords, secret tokens or API keys.</strong> We will never ask for them.</p>
  {app_cards}
  <h2>Security issues</h2>
  <p>Report vulnerabilities privately to <a href="mailto:support@steadyseam.com">support@steadyseam.com</a> and please do
  not disclose them publicly before we have answered. We acknowledge reports within two business days and keep you
  informed until the issue is fixed. Each app's security page describes how the app is built.</p>
  <h2>Your data</h2>
  <p>We use what you send us only to answer you, as described in our <a href="privacy.html">privacy information</a>.</p>
</section>
"""
    with open(os.path.join(HERE, 'support.html'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(page('Support · Steadyseam', 'How to get help with Steadyseam apps for Atlassian Cloud: contact, hours and what to include.', support))
    print('index.html, privacy.html and support.html written,', len(apps), 'apps')


if __name__ == '__main__':
    main()
