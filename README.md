# Steadyseam website

Brand site and app catalog, published with GitHub Pages (user site `gmatera.github.io`, custom domain
`steadyseam.com` once its DNS records are in place).

- `apps.json`: the catalog. To add an app, add an entry and run `python build.py`.
  - `slug`: the name of the app's repository, served under `/<slug>/`; also the anchor of the app on the
    support page (`support.html#<slug>`), where the app's own `support.html` redirects.
  - `status`: `available` (on the Atlassian Marketplace), `review` (in Marketplace review) or `development`.
  - `icon`: path of a square image in `assets/apps/` (empty for a letter placeholder).
  - `links`: the first link is shown as the main button.
  - `support` (optional): what to include in a support request for the app (`include`), an optional warning
    (`note`) and help links; shown on the support page.
- `build.py`: generates `index.html`, `support.html` (the support page for all apps, linked from the
  Marketplace partner profile) and `privacy.html`.
- Each app's own pages (guide, privacy policy, terms) live in the app's repository and are served under
  `/<repository-name>/`, for example `/on-prem-git-svn-for-jira/`.
