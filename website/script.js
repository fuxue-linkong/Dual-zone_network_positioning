// ============================================================
// RadioAreaLocator · 交互脚本（DeepSeek 风格）
// ============================================================

(function () {
    'use strict';

    // 滚动揭示
    const revealTargets = [
        '.hero__badge',
        '.hero__title',
        '.hero__subtitle',
        '.hero__desc',
        '.hero__cta',
        '.section__head',
        '.feature',
        '.note',
        '.stack__group',
        '.dev-card'
    ];

    revealTargets.forEach(selector => {
        document.querySelectorAll(selector).forEach(el => {
            el.setAttribute('data-reveal', '');
        });
    });

    const revealObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('is-visible');
                revealObserver.unobserve(entry.target);
            }
        });
    }, { threshold: 0.08, rootMargin: '0px 0px -40px 0px' });

    document.querySelectorAll('[data-reveal]').forEach(el => revealObserver.observe(el));

    // 导航栏激活态
    const navLinks = document.querySelectorAll('.nav__links a');
    const sections = document.querySelectorAll('main section[id]');

    const navObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const id = entry.target.id;
                navLinks.forEach(link => {
                    link.style.color = link.getAttribute('href') === '#' + id
                        ? 'var(--text-primary)'
                        : '';
                });
            }
        });
    }, { rootMargin: '-40% 0px -55% 0px' });

    sections.forEach(sec => navObserver.observe(sec));

    // 动态获取 GitHub Releases 最新版本并更新 badge
    (function () {
        const badge = document.getElementById('release-badge');
        if (!badge) return;

        const REPO = 'fuxue-linkong/Dual-zone_network_positioning';
        const API  = `https://api.github.com/repos/${REPO}/releases/latest`;

        fetch(API, { headers: { 'Accept': 'application/vnd.github+json' } })
            .then(r => {
                if (!r.ok) throw new Error(`HTTP ${r.status}`);
                return r.json();
            })
            .then(data => {
                const tag = data.tag_name || 'unknown';
                const isPre = !!data.prerelease;
                const suffix = isPre ? ' · 预发布' : ' · 现已上线';
                badge.textContent = tag + suffix;
            })
            .catch(() => {
                // 网络失败时回退显示静态文本
                badge.textContent = 'v2.0.1 · 现已上线';
            });
    })();
})();
