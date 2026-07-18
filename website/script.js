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
})();
