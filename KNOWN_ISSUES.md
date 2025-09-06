# Wakt - Known Issues & Future Improvements

## 🐛 Critical Bugs to Fix

### 1. Internet Speed Degradation
**Severity**: High
**Description**: Internet slows down significantly after blocking a website
**Impact**: Affects overall device internet performance
**Status**: Reported

**Symptoms**:
- Noticeable slowdown in internet browsing after blocking websites
- May affect all network traffic, not just blocked sites
- Performance degradation persists even when accessing non-blocked sites

**Likely Cause**:
- VPN service processing all network traffic inefficiently
- Packet processing loop causing bottlenecks
- DNS resolution delays for all requests

**Potential Solutions**:
- Optimize VPN packet processing to only intercept DNS queries
- Implement more selective traffic filtering
- Add network performance monitoring
- Consider alternative blocking methods (hosts file, DNS server)

---

### 2. Browser Loop Issue with Open Tabs
**Severity**: High
**Description**: Browser gets stuck in loop when blocked site is open in existing tab
**Impact**: Browser becomes unusable, constant interruption
**Status**: Reported

**Symptoms**:
- Block overlay appears repeatedly
- Browser tries to close tab automatically
- Loop continues indefinitely: tab close → block overlay → tab close → repeat
- User cannot escape the cycle

**Root Cause**:
- Accessibility service detects URL in existing tab
- Browser tab closing logic triggers repeatedly
- No detection of "tab already processed" state

**Potential Solutions**:
- Track processed tabs to avoid re-processing
- Improve tab closing detection logic
- Add timeout/cooldown for tab closing attempts
- Better browser state management

---

### 3. App Blocking Bypass Issue
**Severity**: High
**Description**: App blocking doesn't work reliably after dismissing block screen
**Impact**: Primary functionality failure, users can bypass blocks
**Status**: Reported

**Symptoms**:
- Block overlay shows once, then app continues to work normally
- App running in background bypasses blocking detection
- Dismissing block screen allows continued app usage
- Block detection doesn't trigger for subsequent app usage

**Root Cause**:
- Accessibility service not detecting app state changes properly
- Background app detection insufficient
- App launch vs. app resume detection issues
- Cooldown period too permissive

**Potential Solutions**:
- Improve background app state monitoring
- Enhance app launch vs. resume detection
- Implement persistent blocking overlay
- Add process monitoring for blocked apps
- Review cooldown logic for apps

---

## 📋 Technical Debt & Improvements

### Performance Optimizations Needed

#### VPN Service Optimization
- [ ] Implement selective packet filtering (DNS only)
- [ ] Add network performance metrics
- [ ] Optimize packet processing algorithms
- [ ] Consider DNS-over-HTTPS blocking alternatives

#### Accessibility Service Optimization
- [ ] Improve app state detection accuracy
- [ ] Add background process monitoring
- [ ] Implement smarter event filtering
- [ ] Reduce false positive triggers

### User Experience Improvements

#### Blocking Reliability
- [ ] Persistent blocking overlays that can't be easily bypassed
- [ ] Better feedback when blocks are active
- [ ] Improved error handling and recovery
- [ ] Block effectiveness monitoring

#### Browser Integration
- [ ] Better cross-browser compatibility
- [ ] Improved tab management
- [ ] Enhanced URL detection reliability
- [ ] Support for more browser types

### System Integration

#### Permission Management
- [ ] Better permission request flow
- [ ] Graceful degradation when permissions denied
- [ ] Permission status monitoring
- [ ] User guidance for permission setup

#### Background Services
- [ ] Service lifecycle optimization
- [ ] Better system resource management
- [ ] Service crash recovery
- [ ] Battery usage monitoring

---

## 🔍 Investigation Required

### Network Performance Issues
- [ ] Profile VPN service network impact
- [ ] Measure DNS resolution delays
- [ ] Test with different network conditions
- [ ] Compare with other VPN-based blocking apps

### Browser Compatibility Testing
- [ ] Test with Chrome variations (Beta, Dev, Canary)
- [ ] Test with Firefox variations
- [ ] Test with Samsung Internet
- [ ] Test with other popular browsers (Opera, Brave, etc.)

### App State Management
- [ ] Research Android app lifecycle edge cases
- [ ] Test with different app types (games, social, productivity)
- [ ] Investigate multi-tasking scenarios
- [ ] Test with split-screen and picture-in-picture modes

---

## 📊 Testing Requirements

### Performance Testing
- [ ] Network speed benchmarks before/after blocking
- [ ] Battery usage over 24-hour periods
- [ ] Memory usage monitoring
- [ ] CPU usage profiling

### Functionality Testing
- [ ] Cross-browser blocking reliability
- [ ] App blocking across different app types
- [ ] Edge case scenarios (network changes, permissions revoked)
- [ ] Long-term usage stability

### User Experience Testing
- [ ] Block bypass attempts
- [ ] Recovery from stuck states
- [ ] Permission setup flow
- [ ] Challenge completion rates

---

## 🎯 Priority Roadmap

### Phase 1: Critical Bug Fixes (High Priority)
1. **Fix internet speed degradation** - VPN optimization
2. **Resolve browser loop issue** - Tab management improvement
3. **Fix app blocking bypass** - Enhanced state detection

### Phase 2: Reliability Improvements (Medium Priority)
1. **Improve blocking persistence** - Make blocks harder to bypass
2. **Enhanced browser compatibility** - Support more browsers reliably
3. **Better error handling** - Graceful failure recovery

### Phase 3: Performance & UX (Low Priority)
1. **Network performance optimization** - Minimize impact on internet speed
2. **Battery usage reduction** - Further optimize background services
3. **User experience polish** - Smoother permission flows and feedback

---

## 🧪 Experimental Features to Consider

### Alternative Blocking Methods
- [ ] Hosts file modification (requires root)
- [ ] DNS server approach (no VPN required)
- [ ] App overlay injection
- [ ] System-level process monitoring

### Advanced Features
- [ ] Scheduled blocking (time-based rules)
- [ ] Usage analytics and insights
- [ ] Social features (shared block lists)
- [ ] Advanced challenge types (math problems, typing tests)

---

## 📝 Notes for Contributors

### Testing Environment
- Test on multiple Android versions (API 24+)
- Use both emulator and physical devices
- Test with various network conditions
- Include low-end and high-end device testing

### Code Quality
- Maintain comprehensive logging for debugging
- Add performance monitoring hooks
- Implement proper error handling
- Follow Android best practices for background services

### User Feedback Integration
- Set up crash reporting
- Implement usage analytics (privacy-conscious)
- Create feedback collection mechanism
- Monitor app store reviews for issues

---

**Last Updated**: $(date)
**Status**: Active development and bug tracking