const views = {
 overview: `<h2>نمای کلی</h2><p class="notice">اطلاعات این صفحه نمونه هستند؛ داده واقعی از سرور دریافت نشده است.</p><div class="cards"><div class="card">سانترال‌های نمونه<strong>۱</strong><span class="muted">صرفاً نمایشی</span></div><div class="card">داخلی‌های نمونه<strong>۳</strong><span class="muted">صرفاً نمایشی</span></div><div class="card">تماس‌های واقعی<strong>۰</strong><span class="muted">ماژول تماس فعال نیست</span></div></div>`,
 extensions: `<h2>مدیریت داخلی‌ها</h2><p class="muted">نمونه نمایش داخلی‌ها؛ ثبت، ویرایش و اتصال SIP هنوز پیاده‌سازی نشده‌اند.</p><div class="row"><span>مدیریت <span class="code">101</span></span><span class="tag">نمونه</span></div><div class="row"><span>پشتیبانی <span class="code">102</span></span><span class="tag">نمونه</span></div><div class="row"><span>فروش <span class="code">103</span></span><span class="tag">نمونه</span></div>`,
 devices: `<h2>فعال‌سازی دستگاه</h2><p class="notice">برای جلوگیری از صدور اعتبارنامه جعلی یا ناامن، QR واقعی فقط بعد از احراز هویت و پیاده‌سازی Provisioning سمت سرور فعال خواهد شد.</p><p>مسیرهای برنامه‌ریزی‌شده: MOLIDO QR، پروفایل SIP استاندارد و تنظیمات اختصاصی سازندگان پشتیبانی‌شده.</p><button type="button" disabled title="هنوز پیاده‌سازی نشده">صدور QR (غیرفعال)</button>`,
 compat: `<h2>سازگاری با کلاینت‌ها</h2><p>هدف: کلاینت‌های استاندارد SIP از جمله Linphone، Zoiper و تلفن‌های IP. اتصال 3CX در سطح SIP Trunk نیازمند سرور واقعی و تست است.</p><p class="notice">هیچ یک از اتصال‌های بین‌برندی هنوز تست عملیاتی و تأیید نشده‌اند.</p>`
};
const root=document.getElementById('content');
function render(view){root.innerHTML=views[view]??views.overview;document.querySelectorAll('.tab').forEach(b=>{const active=b.dataset.view===view;b.classList.toggle('active',active);b.setAttribute('aria-pressed',String(active));});}
document.querySelectorAll('.tab').forEach(b=>b.addEventListener('click',()=>render(b.dataset.view)));
render('overview');
if('serviceWorker' in navigator && location.protocol==='https:') navigator.serviceWorker.register('./sw.js').catch(()=>{});

// PWA update checks: foreground activation + periodic checks while the page is open.
// Do not force-reload during a call; this preview does not yet support calls.
if ('serviceWorker' in navigator && location.protocol === 'https:') {
  navigator.serviceWorker.ready.then(registration => {
    const check = () => registration.update().catch(() => {});
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') check();
    });
    setInterval(check, 60 * 60 * 1000);
  });
  let reloaded = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (reloaded) return;
    reloaded = true;
    location.reload();
  });
}
