# Theme Selection Guide

This guide helps you choose the right Cloudflare Turnstile theme variant for your Keycloak installation.

## Overview

The Cloudflare Turnstile provider offers **two theme variants** to ensure compatibility across different Keycloak versions:

| Theme Variant | Parent Theme | Target Keycloak | PatternFly Version | Status |
|---------------|--------------|-----------------|-------------------|---------|
| **cloudflare-turnstile** | keycloak.v2 | 25-26+ | PatternFly 5 | ✅ Recommended |
| **cloudflare-turnstile-legacy** | keycloak | 24.x | PatternFly 3/4 | ⚠️ Legacy Support |

## Quick Decision Tree

```
What version of Keycloak are you running?

├─ Keycloak 26+ (Latest)
│  └─ Use: cloudflare-turnstile (modern variant)
│     ✅ Future-proof, full feature support
│
├─ Keycloak 25.x (Current)
│  ├─ Currently using keycloak.v2 theme?
│  │  └─ Use: cloudflare-turnstile (modern variant)
│  │
│  └─ Currently using classic keycloak theme?
│     └─ Use: cloudflare-turnstile-legacy
│        (Plan migration to modern variant)
│
└─ Keycloak 24.x (LTS)
   └─ Use: cloudflare-turnstile-legacy
      ⚠️ Classic keycloak theme deprecated in KC26+
      📅 Plan upgrade path to Keycloak 26+ and modern variant
```

## Theme Variant Details

### Modern Variant: `cloudflare-turnstile`

**For Keycloak 25+ (optimized for 26+)**

#### Features
- Based on `keycloak.v2` theme with PatternFly 5
- Modern, responsive design with dark mode support
- Full wrapper div structure for proper form styling
- Future-proof and actively maintained
- Shared resources via common import

#### When to Use
- ✅ New Keycloak installations (26+)
- ✅ Upgrading from Keycloak 24.x to 25-26+
- ✅ Already using keycloak.v2 theme
- ✅ Want modern UI/UX with dark mode

#### Template Structure
```ftl
<div class="${properties.kcFormGroupClass!}">
    <div class="${properties.kcLabelWrapperClass!}">
        <label>...</label>
    </div>
    <div class="${properties.kcInputWrapperClass!}">
        <input>...</input>
    </div>
</div>
```

#### Browser Requirements
- Modern browsers with CSS Grid and Flexbox support
- Same requirements as Keycloak 26+

---

### Legacy Variant: `cloudflare-turnstile-legacy`

**For Keycloak 24.x**

#### Features
- Based on classic `keycloak` (v1) theme with PatternFly 3/4
- Simpler form structure without modern wrapper divs
- Compatible with older Keycloak installations
- Maintenance mode (security fixes only)
- Shared resources via common import

#### When to Use
- ✅ Running Keycloak 24.x
- ✅ Cannot upgrade to Keycloak 26+ yet
- ✅ Using classic keycloak theme
- ⚠️ Temporary solution during migration planning

#### Template Structure
```ftl
<div class="${properties.kcFormGroupClass!}">
    <label>...</label>
    <input>...</input>
</div>
```

#### Sunset Timeline
- **Supported**: While Keycloak classic theme exists (through KC 25.x)
- **Deprecated**: When Keycloak removes classic theme (KC 26+)
- **Removal**: Approximately 6 months after Keycloak drops classic theme support

---

## Configuration Instructions

### Step 1: Select Theme in Keycloak Admin Console

1. Log in to Keycloak Admin Console
2. Navigate to your realm (e.g., `turnstile-demo`)
3. Click **Realm Settings** in the left sidebar
4. Click the **Themes** tab
5. In the **Login Theme** dropdown, select:
   - `cloudflare-turnstile` (for modern variant)
   - `cloudflare-turnstile-legacy` (for legacy variant)
6. Click **Save**

### Step 2: Configure Turnstile Authenticator

1. Navigate to **Authentication** → **Flows**
2. Select your flow (e.g., `browser` or `registration`)
3. Find the Turnstile authenticator execution
4. Click the **gear icon** (⚙️) to configure
5. Set **Implementation Method** to:
   - **CUSTOM_THEME** - Uses the selected theme variant (requires theme selection above)
   - **SCRIPT_INJECTION** - Works with any theme (theme-agnostic)
   - **SEPARATE_PAGE** - Standalone Turnstile page (theme-agnostic)

### Step 3: Test the Integration

1. Open your realm's login page in a private/incognito window
2. Verify the Turnstile widget appears correctly
3. Test form submission with both successful and failed verifications
4. Check form styling matches your selected theme

---

## Implementation Methods

The theme variant you choose only affects **CUSTOM_THEME** mode. Other implementation methods are theme-agnostic.

### CUSTOM_THEME (Theme-Dependent)

**Requires**: Manual theme selection (cloudflare-turnstile or cloudflare-turnstile-legacy)

**How it works**:
- Uses the selected theme's `login.ftl` and `register.ftl` templates
- Theme templates include native Turnstile widget code
- Widget appears inline on login/registration forms
- Styling inherits from parent theme

**Best for**:
- Users who want native theme integration
- Consistent branding across login flow
- Full control over widget placement

### SCRIPT_INJECTION (Theme-Agnostic)

**Requires**: No theme selection needed (works with any theme)

**How it works**:
- JavaScript automatically detects form and injects Turnstile widget
- Works with base, keycloak, keycloak.v2, or any custom theme
- Adapts to form structure via DOM manipulation
- Uses shared `turnstile-injector.js` from common resources

**Best for**:
- Users who don't want to change themes
- Mixed environments with multiple themes
- Maximum compatibility

### SEPARATE_PAGE (Theme-Agnostic)

**Requires**: No theme selection needed

**How it works**:
- Shows standalone Turnstile verification page
- After verification, redirects to login form
- Uses bundled template from JAR file

**Best for**:
- Maximum compatibility
- Environments with strict theme policies
- Simplest deployment

---

## Version Compatibility Matrix

| Theme Variant | KC 24.x | KC 25.x | KC 26.x | KC 27.x+ |
|---------------|---------|---------|---------|----------|
| **cloudflare-turnstile** | ⚠️ | ✅ | ✅ | ✅ |
| **cloudflare-turnstile-legacy** | ✅ | ✅ | ⚠️ | ❌ |

**Legend**:
- ✅ Fully supported and tested
- ⚠️ Compatible but not recommended
- ❌ Not supported

---

## Migration Scenarios

### Scenario 1: Upgrading from Keycloak 24 → 26

**Before upgrade**:
- Theme: `cloudflare-turnstile-legacy`
- Keycloak: 24.x

**After upgrade**:
1. Upgrade Keycloak to 26.x
2. Navigate to Realm Settings → Themes
3. Change Login Theme to `cloudflare-turnstile`
4. Click Save
5. Test login/registration flows
6. Verify Turnstile widget displays correctly

**Rollback**: Change theme back to `cloudflare-turnstile-legacy` if issues occur

### Scenario 2: New Installation (Keycloak 26+)

**Recommended**:
1. Install Cloudflare Turnstile provider
2. Configure authenticator with CUSTOM_THEME method
3. Select `cloudflare-turnstile` theme
4. No need for legacy variant

### Scenario 3: Stuck on Keycloak 24 Long-Term

**Recommendation**:
1. Use `cloudflare-turnstile-legacy` theme
2. Monitor Keycloak release notes for classic theme deprecation timeline
3. Plan migration to Keycloak 26+ before support ends
4. Alternative: Switch to SCRIPT_INJECTION method (theme-agnostic)

---

## Troubleshooting

### Widget Not Displaying

**Problem**: Turnstile widget doesn't appear on login form

**Solutions**:
1. **Verify theme selection**:
   - Admin Console → Realm Settings → Themes → Login Theme
   - Ensure `cloudflare-turnstile` or `cloudflare-turnstile-legacy` is selected

2. **Check implementation method**:
   - Authentication → Flows → Turnstile config → Implementation Method
   - Should be set to `CUSTOM_THEME`

3. **Clear browser cache**: Hard refresh (Ctrl+F5 / Cmd+Shift+R)

4. **Check logs**: Look for FreeMarker template errors in Keycloak logs

5. **Try SCRIPT_INJECTION**: Switch to theme-agnostic method as workaround

### Styling Issues / Layout Broken

**Problem**: Form fields misaligned, double margins, nested containers

**Solutions**:
1. **Verify Keycloak version matches theme variant**:
   - KC 26+ → Use `cloudflare-turnstile` (modern)
   - KC 24.x → Use `cloudflare-turnstile-legacy`

2. **Check for theme customizations**:
   - Custom CSS may conflict with template structure
   - Review realm theme properties and custom styles

3. **Browser DevTools inspection**:
   - Open browser DevTools (F12)
   - Inspect Turnstile widget and surrounding form groups
   - Check for proper CSS class application

4. **Switch variants**: Try the other theme variant to isolate the issue

### Dark Mode Issues (Modern Variant Only)

**Problem**: Turnstile widget doesn't match dark mode

**Solutions**:
1. **Check widget theme setting**:
   - Authenticator config → Widget Theme → Set to `auto`
   - Or manually set `dark` for dark mode

2. **Verify keycloak.v2 theme**: Ensure realm uses keycloak.v2 or theme that extends it

3. **Test browser dark mode**: Toggle system/browser dark mode preference

---

## Advanced Configuration

### Custom Theme Based on Turnstile Variants

You can create your own theme that extends Turnstile variants:

**For modern custom theme**:
```properties
# my-custom-theme/login/theme.properties
parent=cloudflare-turnstile
# Your customizations here
```

**For legacy custom theme**:
```properties
# my-legacy-theme/login/theme.properties
parent=cloudflare-turnstile-legacy
# Your customizations here
```

### Shared Resources

Both theme variants share common resources via:
```properties
import=common/cloudflare-turnstile
```

This imports:
- `turnstile-injector.js` - JavaScript for SCRIPT_INJECTION mode
- Future shared resources (CSS, etc.)

---

## FAQ

### Q: Can I use both theme variants simultaneously?

**A**: No. Each realm can only have one login theme selected. However, different realms can use different variants.

### Q: Does switching themes require downtime?

**A**: No. Theme changes take effect immediately for new sessions. Existing sessions continue until logout/expiration.

### Q: Will the legacy variant receive new features?

**A**: No. Legacy variant is in maintenance mode and receives only security fixes. New features are added to the modern variant only.

### Q: Can I switch between variants without losing configuration?

**A**: Yes. Turnstile authenticator configuration (site key, secret key, etc.) is stored separately from theme selection. Switching themes only affects UI rendering.

### Q: What happens if I use the wrong variant for my Keycloak version?

**A**: The widget should still work, but you may experience styling issues like misaligned fields, double margins, or layout problems. Use the recommended variant for your Keycloak version.

### Q: Do I need to rebuild or redeploy when switching themes?

**A**: No. Both theme variants are included in the same JAR file. Simply change the theme selection in Admin Console.

---

## Support and Resources

- **Documentation**: [docs/README.md](README.md)
- **Setup Guide**: [docs/SETUP.md](SETUP.md)
- **Troubleshooting**: [docs/TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- **GitHub Issues**: [Report issues](https://github.com/ZymLabs/keycloak-cloudflare-turnstile-provider/issues)

---

## Version Information

- **Modern Variant**: Based on Keycloak 26.0.0 keycloak.v2 theme (PatternFly 5)
- **Legacy Variant**: Based on Keycloak 24.0.0 keycloak theme (PatternFly 3/4)
- **Last Updated**: 2024-11-20
