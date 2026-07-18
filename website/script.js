/* ════════════════════════════════════════════════════════════
   RadioAreaLocator · 宣传网页交互
   ════════════════════════════════════════════════════════════ */

(function () {
    'use strict';

    // ──────────────────────────────────────────────
    // 1. 滚动揭示动画
    // ──────────────────────────────────────────────
    const revealTargets = [
        '.hero__content',
        '.hero__visual',
        '.feature',
        '.showcase__frame',
        '.showcase__notes .note',
        '.stack__group',
        '.crypto__diagram',
        '.crypto__item',
        '.dev__avatar',
        '.dev__body',
        '.section__head'
    ];

    revealTargets.forEach(selector => {
        document.querySelectorAll(selector).forEach((el, i) => {
            el.setAttribute('data-reveal', '');
            el.style.transitionDelay = `${Math.min(i * 80, 320)}ms`;
        });
    });

    const io = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('is-visible');
                io.unobserve(entry.target);
            }
        });
    }, {
        threshold: 0.1,
        rootMargin: '0px 0px -80px 0px'
    });

    document.querySelectorAll('[data-reveal]').forEach(el => io.observe(el));

    // ──────────────────────────────────────────────
    // 2. 导航高亮（滚动跟随）
    // ──────────────────────────────────────────────
    const navLinks = document.querySelectorAll('.nav__links a');
    const sections = Array.from(navLinks).map(link => {
        const id = link.getAttribute('href').replace('#', '');
        return document.getElementById(id);
    }).filter(Boolean);

    const navObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const id = entry.target.id;
                navLinks.forEach(link => {
                    const isActive = link.getAttribute('href') === `#${id}`;
                    link.style.color = isActive ? 'var(--phosphor)' : '';
                });
            }
        });
    }, { threshold: 0.3, rootMargin: '-80px 0px -50% 0px' });

    sections.forEach(s => navObserver.observe(s));

    // ──────────────────────────────────────────────
    // 3. 示波器波形 — 鼠标响应
    // ──────────────────────────────────────────────
    const scope = document.querySelector('.scope');
    const wavePath = document.querySelector('.scope__path');
    if (scope && wavePath) {
        scope.addEventListener('mousemove', (e) => {
            const rect = scope.getBoundingClientRect();
            const x = (e.clientX - rect.left) / rect.width;
            const intensity = 0.5 + Math.abs(x - 0.5);
            wavePath.style.opacity = intensity.toFixed(2);
        });
        scope.addEventListener('mouseleave', () => {
            wavePath.style.opacity = '';
        });
    }

    // ──────────────────────────────────────────────
    // 4. 雷达 blip 随机出现
    // ──────────────────────────────────────────────
    const radar = document.querySelector('.radar');
    if (radar) {
        // 周期性生成临时 blip
        setInterval(() => {
            const blip = document.createElement('div');
            blip.className = 'radar__blip';
            const angle = Math.random() * Math.PI * 2;
            const radius = 20 + Math.random() * 60;
            const cx = 90, cy = 90; // 中心
            const x = cx + Math.cos(angle) * radius;
            const y = cy + Math.sin(angle) * radius;
            blip.style.left = `${x}px`;
            blip.style.top = `${y}px`;
            blip.style.background = Math.random() > 0.6 ? 'var(--amber)' : 'var(--phosphor-bright)';
            blip.style.boxShadow = `0 0 8px ${blip.style.background}`;
            blip.style.animation = 'blip-fade 3s ease-out forwards';
            radar.appendChild(blip);
            setTimeout(() => blip.remove(), 3000);
        }, 2500);

        // 动态注入 blip-fade 动画
        const style = document.createElement('style');
        style.textContent = `
            @keyframes blip-fade {
                0% { opacity: 0; transform: scale(0.5); }
                20% { opacity: 1; transform: scale(1.3); }
                100% { opacity: 0; transform: scale(1); }
            }
        `;
        document.head.appendChild(style);
    }

    // ──────────────────────────────────────────────
    // 5. 数字滚动统计
    // ──────────────────────────────────────────────
    const stats = document.querySelectorAll('.stat__num');
    const statObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (!entry.isIntersecting) return;
            const el = entry.target;
            const raw = el.textContent.trim();
            const match = raw.match(/^(\d+)/);
            if (!match) {
                statObserver.unobserve(el);
                return;
            }
            const target = parseInt(match[1], 10);
            const suffix = raw.slice(match[1].length);
            const duration = 1200;
            const start = performance.now();
            const step = (now) => {
                const t = Math.min((now - start) / duration, 1);
                const eased = 1 - Math.pow(1 - t, 3);
                el.textContent = Math.round(target * eased) + suffix;
                if (t < 1) requestAnimationFrame(step);
            };
            requestAnimationFrame(step);
            statObserver.unobserve(el);
        });
    }, { threshold: 0.5 });

    stats.forEach(s => statObserver.observe(s));

    // ──────────────────────────────────────────────
    // 6. 顶部 ticker 鼠标悬停暂停
    // ──────────────────────────────────────────────
    const tickerTrack = document.querySelector('.ticker__track');
    if (tickerTrack) {
        const ticker = document.querySelector('.ticker');
        ticker.addEventListener('mouseenter', () => {
            tickerTrack.style.animationPlayState = 'paused';
        });
        ticker.addEventListener('mouseleave', () => {
            tickerTrack.style.animationPlayState = 'running';
        });
    }

    // ──────────────────────────────────────────────
    // 7. 控制台签名
    // ──────────────────────────────────────────────
    const styles = [
        'color: #36D167',
        'font-family: monospace',
        'font-size: 12px',
        'line-height: 1.6'
    ].join(';');
    console.log('%c·· ·−·· −−· ··−−·· ·− −·· ·· ·−−· ·− −−− ··−·· − ·− ··· ·· ···−· ·· ·− −·', styles);
    console.log('%cRadioAreaLocator · 73 · SK', 'color: #36D167; font-family: monospace; font-size: 14px; font-weight: bold;');
    console.log('%c双区网络定位 — 为业余无线电爱好者而生', 'color: #8B938A; font-family: monospace; font-size: 11px;');
})();
