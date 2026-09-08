(() => {
  if (window.__studyLockTerm3SystemAttached) return;
  window.__studyLockTerm3SystemAttached = true;

  const PROFILE_KEY = 'studylock_student_profile_v3';
  const SCHEDULE_KEY = 'studylock_parent_schedule_v3';
  const AUTO_DATE_KEY = 'studylock_parent_schedule_last_start_v3';
  const AI_USAGE_KEY = 'studylock_ai_usage_count_v3';
  const QUIZ_COUNT_KEY = 'studylock_quiz_count_v3';
  const LAST_REMOTE_START = 'studylock_admin_last_start_v1';
  const LAST_REMOTE_END = 'studylock_admin_last_end_v1';
  const native = window.StudyLockNative;

  const FORMULAS = {
    'Mathematics': ['percentage=(part/whole)×100','mean=sum(values)/count','speed=distance/time','A rectangle=l×w','A triangle=½bh','C=2πr','A=πr²','a²+b²=c²','gradient=(y₂-y₁)/(x₂-x₁)'],
    'Mathematical Literacy': ['percentage=(part/whole)×100','distance=speed×time','simple interest I=P×i×n','compound amount A=P(1+i)ⁿ'],
    'Physical Sciences': ['average speed=distance/time','a=Δv/Δt','v_f=v_i+aΔt','Δx=v_iΔt+½a(Δt)²','v_f²=v_i²+2aΔx','E_k=½mv²','E_p=mgh','V=IR','Q=IΔt','c=n/V','n=m/M'],
    'Natural Sciences': ['speed=distance/time','density=mass/volume','V=IR'],
    'Geography': ['ground distance=map distance×scale denominator','gradient=vertical interval/horizontal equivalent'],
    'Accounting': ["Assets=Owner's Equity+Liabilities",'gross profit=sales-cost of sales','net profit=income-expenses'],
    'Economics': ['percentage change=((new-old)/old)×100']
  };

  const PHASES = {
    foundation: {
      'Mathematics': [
        ['Number sense and counting','Count, compare and order numbers; practise place value and number bonds.'],
        ['Addition and subtraction','Use mental and written strategies to solve addition and subtraction problems.'],
        ['Patterns','Recognise, continue and describe simple number and shape patterns.'],
        ['Measurement','Compare and measure length, mass, capacity and time using appropriate units.'],
        ['Data handling','Collect simple data and represent it with pictures, tallies or simple graphs.']
      ],
      'Home Language': [
        ['Reading fluency','Read grade-appropriate texts accurately and with understanding.'],
        ['Comprehension','Identify characters, setting, sequence, main idea and key details.'],
        ['Vocabulary','Learn new words from context and use them in sentences.'],
        ['Writing','Plan and write short sentences or paragraphs with correct punctuation.']
      ],
      'First Additional Language': [
        ['Everyday vocabulary','Build useful vocabulary for school, home and community.'],
        ['Listening comprehension','Listen to short texts and answer simple questions.'],
        ['Reading','Read short sentences and texts with increasing fluency.'],
        ['Writing','Write short guided sentences and simple paragraphs.']
      ],
      'Life Skills': [
        ['Healthy living','Identify habits that support health, hygiene and safety.'],
        ['Personal and social wellbeing','Practise respectful behaviour, emotions vocabulary and cooperation.'],
        ['Beginning knowledge','Explore people, places, seasons, materials and the local environment.'],
        ['Creative arts','Use drawing, music, movement and drama to communicate ideas.']
      ]
    },
    intermediate: {
      'Mathematics': [
        ['Whole numbers','Work with place value, factors, multiples and the four operations.'],
        ['Fractions and decimals','Compare, order and calculate with common fractions and decimals.'],
        ['Patterns and algebra','Continue numeric patterns and describe rules.'],
        ['Geometry','Classify 2D shapes and 3D objects and reason about angles and symmetry.'],
        ['Measurement','Calculate or estimate length, perimeter, area, volume, mass and time.'],
        ['Data handling','Read, create and interpret tables and graphs.']
      ],
      'English': [
        ['Reading comprehension','Identify main ideas, details and inference in literary and informational texts.'],
        ['Summary writing','Select important ideas and restate them concisely.'],
        ['Language structures','Use sentence structure, punctuation and tense accurately.'],
        ['Creative writing','Plan, draft, revise and edit descriptive or narrative writing.']
      ],
      'Natural Sciences and Technology': [
        ['Matter and materials','Describe properties of materials and changes between states.'],
        ['Energy and systems','Explain simple energy transfer and basic electrical or mechanical systems.'],
        ['Life and living','Relate structures of plants and animals to their functions.'],
        ['Planet Earth and beyond','Describe Earth, weather, seasons and basic space relationships.'],
        ['Design process','Investigate a need, design a solution, make or model it and evaluate it.']
      ],
      'Social Sciences': [
        ['Map skills','Use direction, symbols, scale and simple maps.'],
        ['Weather and climate','Describe weather elements and patterns.'],
        ['Population and settlements','Explain settlement patterns and why people live in different places.'],
        ['History sources','Use written, visual and material sources to answer questions about the past.']
      ],
      'Life Skills': [
        ['Study habits','Plan schoolwork, set goals and reflect on progress.'],
        ['Health and safety','Identify age-appropriate health, safety and wellbeing choices.'],
        ['Citizenship','Understand responsibilities, respect and participation in the community.']
      ]
    },
    senior: {
      'Mathematics': [
        ['Numbers and exponents','Work accurately with integers, rational numbers, exponents and scientific notation.'],
        ['Algebra','Simplify expressions, solve equations and use algebraic rules.'],
        ['Functions and graphs','Interpret relationships between variables using tables, equations and graphs.'],
        ['Geometry','Use angle relationships and properties of triangles and quadrilaterals.'],
        ['Measurement','Solve perimeter, area, surface area and volume problems.'],
        ['Data handling and probability','Summarise data and calculate simple probabilities.']
      ],
      'Natural Sciences': [
        ['Matter and chemical change','Use particle ideas, elements, compounds, mixtures and chemical reactions.'],
        ['Forces and motion','Describe motion, forces and energy transfer.'],
        ['Electricity','Explain current, voltage, resistance and circuit behaviour.'],
        ['Life and living','Study cells, systems, reproduction, ecosystems and biodiversity.'],
        ['Earth and space','Connect atmosphere, Earth systems and space science.']
      ],
      'English': [
        ['Critical reading','Analyse purpose, audience, tone, inference and evidence.'],
        ['Writing','Plan and produce essays and transactional texts with clear structure.'],
        ['Language','Edit grammar, punctuation, sentence structure and vocabulary.'],
        ['Literature','Analyse character, theme, imagery, structure and literary devices.']
      ],
      'Geography': [
        ['Mapwork','Use scale, direction, coordinates, contours and basic GIS ideas.'],
        ['Weather and climate','Interpret weather data and explain climate controls.'],
        ['Population','Analyse population distribution, change and movement.'],
        ['Settlements','Compare rural and urban settlement patterns and functions.'],
        ['Resources and sustainability','Evaluate resource use and environmental impact.']
      ],
      'History': [
        ['Historical evidence','Evaluate source origin, purpose, reliability and usefulness.'],
        ['Cause and consequence','Explain multiple causes and consequences.'],
        ['Change and continuity','Identify what changed, what remained and why.'],
        ['Historical argument','Construct evidence-based paragraphs and essays.']
      ],
      'Economic Management Sciences': [
        ['The economy','Explain economic participants, markets and flows.'],
        ['Entrepreneurship','Identify needs, opportunities, resources and business ideas.'],
        ['Financial literacy','Use budgets, income, expenses and basic accounting records.'],
        ['Business functions','Describe production, marketing, finance and human resources.']
      ],
      'Technology': [
        ['Design process','Investigate, design, make, evaluate and communicate.'],
        ['Structures','Analyse loads, forces and structural strength.'],
        ['Mechanical systems','Use gears, levers and mechanical advantage.'],
        ['Electrical systems','Interpret simple circuit and control ideas.']
      ],
      'Life Orientation': [
        ['Self-development','Set goals and use practical strategies for learning and wellbeing.'],
        ['Careers','Explore subjects, careers and pathways.'],
        ['Citizenship','Apply rights, responsibilities and respectful participation.']
      ]
    },
    fet: {
      'Mathematics': [
        ['Algebra','Manipulate expressions and solve equations and inequalities.'],
        ['Functions','Represent and interpret functions using equations and graphs.'],
        ['Trigonometry','Use trigonometric ratios, identities and equations where appropriate.'],
        ['Analytical geometry','Use coordinates, gradient, distance and midpoint relationships.'],
        ['Euclidean geometry','Use definitions, theorems and logical reasoning.'],
        ['Statistics and probability','Analyse data and calculate probabilities.'],
        ['Finance and growth','Model simple and compound growth and depreciation.']
      ],
      'Mathematical Literacy': [
        ['Finance','Interpret budgets, banking, interest, tax and financial documents.'],
        ['Measurement','Use units, scale, perimeter, area, volume and conversions.'],
        ['Maps and plans','Interpret scale, routes, plans and spatial information.'],
        ['Data handling','Analyse tables, graphs, measures and claims.'],
        ['Probability','Use probability to interpret risk and everyday situations.']
      ],
      'Physical Sciences': [
        ['Mechanics','Use vectors, motion descriptions, forces and energy as appropriate to grade.'],
        ['Waves and sound','Explain wave behaviour, sound and related quantities.'],
        ['Electricity and magnetism','Use circuit relationships and electrical calculations.'],
        ['Matter and materials','Connect structure, bonding and properties.'],
        ['Chemical change','Balance reactions and use mole and stoichiometric relationships.']
      ],
      'Life Sciences': [
        ['Cells and molecular biology','Relate cell structures and biological molecules to function.'],
        ['Life processes','Explain major plant and animal life processes.'],
        ['Genetics and reproduction','Use inheritance, meiosis and reproduction concepts where appropriate.'],
        ['Ecology','Analyse populations, communities, ecosystems and human impacts.'],
        ['Evolution and biodiversity','Use evidence and classification to explain diversity and change.']
      ],
      'Geography': [
        ['Climate and weather','Analyse atmospheric processes and weather information.'],
        ['Geomorphology','Explain landforms and processes shaping landscapes.'],
        ['Settlement geography','Analyse settlement patterns, functions and challenges.'],
        ['Economic geography','Relate sectors, resources and development to spatial patterns.'],
        ['Mapwork and GIS','Interpret topographic maps, orthophotos and GIS concepts.']
      ],
      'History': [
        ['Source-based questions','Evaluate evidence, perspective, reliability and usefulness.'],
        ['Essay writing','Build a clear line of argument supported by evidence.'],
        ['Power and society','Analyse political, social and economic change in context.'],
        ['Resistance and change','Compare movements, strategies, consequences and interpretations.']
      ],
      'English': [
        ['Comprehension','Analyse meaning, inference, tone, purpose and argument.'],
        ['Summary','Select essential ideas and express them concisely.'],
        ['Language','Edit grammar, style, register and visual literacy.'],
        ['Writing','Plan and write essays and transactional texts.'],
        ['Literature','Analyse poetry, drama, novels or short stories using evidence.']
      ],
      'Accounting': [
        ['Accounting equation','Record the effect of transactions on assets, equity and liabilities.'],
        ['Journals and ledgers','Record transactions and post them to ledger accounts.'],
        ['Financial statements','Prepare and interpret grade-appropriate financial statements.'],
        ['Reconciliations','Compare records and explain differences.']
      ],
      'Business Studies': [
        ['Business environments','Analyse micro, market and macro environments.'],
        ['Business ventures','Apply entrepreneurship, planning and investment ideas.'],
        ['Business roles','Use teamwork, ethics, professionalism and creative problem solving.'],
        ['Business operations','Explain human resources, quality and operational functions.']
      ],
      'Economics': [
        ['Macroeconomics','Analyse circular flow, national accounts and major economic indicators.'],
        ['Microeconomics','Use demand, supply and market structures.'],
        ['Economic pursuits','Evaluate growth, development and policy approaches.'],
        ['Contemporary issues','Interpret inflation, unemployment, trade and related data.']
      ],
      'Computer Applications Technology': [
        ['Systems technologies','Understand computer hardware, software and file management.'],
        ['Word processing','Create well-structured documents using advanced features.'],
        ['Spreadsheets','Use formulas, functions, charts and data tools.'],
        ['Databases','Organise, query and report structured data.']
      ],
      'Information Technology': [
        ['Programming','Use variables, selection, iteration, methods and data structures.'],
        ['Object orientation','Model programs using classes and objects where appropriate.'],
        ['Databases','Design and query databases and connect them to applications.'],
        ['Systems technologies','Explain hardware, networks and data representation.']
      ],
      'Agricultural Sciences': [
        ['Soil and plant studies','Relate soil properties and plant production to agricultural practice.'],
        ['Animal studies','Explain nutrition, production and reproduction principles.'],
        ['Agricultural management','Use resources, records and basic economic reasoning.']
      ],
      'Tourism': [
        ['Tourism sectors','Explain sectors, services and professional roles.'],
        ['Tour planning','Use routes, time, cost and destination information.'],
        ['Tourism geography','Locate and interpret attractions and destination patterns.'],
        ['Customer care','Apply service, communication and responsible-tourism principles.']
      ],
      'Life Orientation': [
        ['Study and career planning','Set goals, plan learning and investigate pathways.'],
        ['Personal wellbeing','Use responsible decision-making and stress-management strategies.'],
        ['Citizenship','Apply constitutional values, rights and responsibilities.']
      ]
    }
  };

  const OVERRIDES = {
    '8': {
      'Mathematics': [
        ['Data handling','Collect, organise and summarise data; use measures of central tendency and interpret displays.'],
        ['Geometry of straight lines','Use angle relationships formed by perpendicular, intersecting and parallel lines.'],
        ['Geometry of 2D shapes','Classify triangles and investigate angle and side properties.'],
        ['Revision and assessment','Consolidate Term 3 topics and practise mixed problems.']
      ]
    },
    '10': {
      'Physical Sciences': [
        ['Quantitative aspects of chemical change','Use concentration, percentage composition, empirical formulae and stoichiometric calculations.'],
        ['Vectors and scalars','Distinguish vectors from scalars and determine resultants in one dimension.'],
        ['Motion in one dimension','Use position, distance, displacement, speed, velocity and acceleration.'],
        ['Equations of motion','Interpret motion graphs and solve uniformly accelerated motion problems.'],
        ['Energy','Calculate kinetic and gravitational potential energy.']
      ]
    },
    '12': {
      'Geography': [
        ['Structure of the South African economy','Interpret economic sectors, GDP or GNP and employment information.'],
        ['Agriculture and mining','Analyse factors, contributions and challenges in primary activities.'],
        ['Secondary and tertiary sectors','Explain industrial and service-sector patterns and contributions.'],
        ['Industrial regions and development strategies','Analyse industrial regions and selected development strategies.'],
        ['Mapwork and GIS consolidation','Apply topographic-map, orthophoto and GIS skills.']
      ]
    }
  };

  function phaseForGrade(grade) {
    if (grade <= 3) return 'foundation';
    if (grade <= 6) return 'intermediate';
    if (grade <= 9) return 'senior';
    return 'fet';
  }

  function gradeSubjects(grade) {
    const base = PHASES[phaseForGrade(grade)] || PHASES.senior;
    const output = {};
    Object.keys(base).forEach(subject => {
      output[subject] = (OVERRIDES[String(grade)]?.[subject] || base[subject]).map(item => item.slice());
    });
    return output;
  }

  function loadProfile() {
    try {
      const saved = JSON.parse(localStorage.getItem(PROFILE_KEY) || '{}');
      return {
        name: String(saved.name || '').trim(),
        grade: Math.min(12, Math.max(1, Number(saved.grade) || 10)),
        term: Math.min(4, Math.max(1, Number(saved.term) || 3))
      };
    } catch (_) {
      return { name: '', grade: 10, term: 3 };
    }
  }

  function saveProfile(profile, announce = true) {
    const next = {
      name: String(profile.name || '').trim().slice(0, 60),
      grade: Math.min(12, Math.max(1, Number(profile.grade) || 10)),
      term: Math.min(4, Math.max(1, Number(profile.term) || 3))
    };
    localStorage.setItem(PROFILE_KEY, JSON.stringify(next));
    try { native?.updateStudentProfile(next.name, next.grade, next.term); } catch (_) {}
    renderDailyTask();
    syncMetrics();
    if (announce) window.StudyLockNativeHooks?.showToast?.(`Profile saved · Grade ${next.grade} · Term ${next.term}`);
    return next;
  }

  function loadSchedule() {
    try {
      const saved = JSON.parse(localStorage.getItem(SCHEDULE_KEY) || '{}');
      return {
        enabled: !!saved.enabled,
        startMinute: Math.min(1439, Math.max(0, Number(saved.startMinute) || 18 * 60)),
        endMinute: Math.min(1439, Math.max(0, Number(saved.endMinute) || 20 * 60)),
        defaultMinutes: Math.min(300, Math.max(25, Number(saved.defaultMinutes) || 60))
      };
    } catch (_) {
      return { enabled: false, startMinute: 18 * 60, endMinute: 20 * 60, defaultMinutes: 60 };
    }
  }

  function storeSchedule(schedule, announce = true) {
    const next = {
      enabled: !!schedule.enabled,
      startMinute: Math.min(1439, Math.max(0, Number(schedule.startMinute) || 0)),
      endMinute: Math.min(1439, Math.max(0, Number(schedule.endMinute) || 0)),
      defaultMinutes: Math.min(300, Math.max(25, Number(schedule.defaultMinutes) || 60))
    };
    localStorage.setItem(SCHEDULE_KEY, JSON.stringify(next));
    try { native?.setAutoStudySchedule(next.enabled, next.startMinute, next.endMinute, next.defaultMinutes); } catch (_) {}
    refreshScheduleUi();
    if (announce) window.StudyLockNativeHooks?.showToast?.('Parent Auto Study schedule saved.');
    return next;
  }

  function nowMinute() {
    const now = new Date();
    return now.getHours() * 60 + now.getMinutes();
  }

  function withinSchedule(schedule = loadSchedule()) {
    if (!schedule.enabled) return false;
    const current = nowMinute();
    if (schedule.startMinute === schedule.endMinute) return true;
    if (schedule.startMinute < schedule.endMinute) {
      return current >= schedule.startMinute && current < schedule.endMinute;
    }
    return current >= schedule.startMinute || current < schedule.endMinute;
  }

  function minutesUntilEnd(schedule = loadSchedule()) {
    const current = nowMinute();
    let diff = schedule.endMinute - current;
    if (diff <= 0) diff += 1440;
    return Math.max(1, diff);
  }

  function focusActive() {
    return document.getElementById('hero')?.classList.contains('locked') === true;
  }

  function chooseMinutes(minutes) {
    const buttons = Array.from(document.querySelectorAll('.preset-btn'));
    let best = buttons.find(button => Number(button.dataset.mins) === minutes);
    if (!best && buttons.length) {
      best = buttons.reduce((a, b) => Math.abs(Number(a.dataset.mins || 25) - minutes) <= Math.abs(Number(b.dataset.mins || 25) - minutes) ? a : b);
    }
    best?.click();
  }

  function startAutoStudy(message) {
    if (focusActive() || typeof startSession !== 'function') return;
    const schedule = loadSchedule();
    const duration = Math.max(25, Math.min(schedule.defaultMinutes, minutesUntilEnd(schedule)));
    chooseMinutes(duration);
    startSession();
    document.documentElement.dataset.studylockEnforcedSchedule = 'true';
    window.StudyLockNativeHooks?.showToast?.(message || 'Parent Auto Study started automatically.');
  }

  function maybeApplySchedule() {
    const schedule = loadSchedule();
    const date = new Date();
    const dateKey = `${date.getFullYear()}-${date.getMonth()+1}-${date.getDate()}`;
    if (withinSchedule(schedule)) {
      document.documentElement.dataset.studylockEnforcedSchedule = 'true';
      if (!focusActive() && localStorage.getItem(AUTO_DATE_KEY) !== dateKey) {
        localStorage.setItem(AUTO_DATE_KEY, dateKey);
        startAutoStudy('Parent Auto Study started for today.');
      }
      return;
    }
    document.documentElement.dataset.studylockEnforcedSchedule = 'false';
  }

  function removePauseControls() {
    const candidates = Array.from(document.querySelectorAll('button,a,[role="button"]'));
    candidates.forEach(node => {
      const label = `${node.textContent || ''} ${node.getAttribute('aria-label') || ''}`.trim().toLowerCase();
      if (label.includes('pause') && !label.includes('music')) node.remove();
    });
  }

  const originalPause = typeof window.pauseSession === 'function' ? window.pauseSession : null;
  if (originalPause) window.pauseSession = () => window.StudyLockNativeHooks?.showToast?.('StudyLock sessions cannot be paused.');

  document.addEventListener('click', event => {
    const target = event.target instanceof Element ? event.target : null;
    const action = target?.closest('#mainAction,.main-action,[data-action="end"],#endSessionBtn');
    if (!action || !focusActive() || !withinSchedule()) return;
    if (window.__studyLockTrustedParentCommand === true) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    window.StudyLockNativeHooks?.showToast?.('This study session is controlled by the parent schedule and ends at the scheduled time.');
  }, true);

  function curriculumContainer() {
    const offline = document.getElementById('offlineTutorLibrarySection');
    if (offline?.parentElement) return offline.parentElement;
    const data = Array.from(document.querySelectorAll('.settings-section')).find(section => section.querySelector('.settings-label')?.textContent?.trim() === 'Data');
    return data?.parentElement || document.querySelector('[id*="settings" i]') || document.querySelector('.app');
  }

  function ensureProfileUi() {
    if (document.getElementById('studylockCurriculumProfileSection')) return;
    const parent = curriculumContainer();
    if (!parent) return;
    const profile = loadProfile();
    const section = document.createElement('div');
    section.className = 'settings-section';
    section.id = 'studylockCurriculumProfileSection';
    section.innerHTML = `
      <div class="settings-label">Student profile & curriculum</div>
      <div class="settings-row" style="display:block;">
        <div class="settings-row-label">Profile name</div>
        <input id="studylockProfileName" class="settings-input" maxlength="60" value="${profile.name.replace(/&/g,'&amp;').replace(/"/g,'&quot;')}">
      </div>
      <div class="settings-row" style="display:block;">
        <div class="settings-row-label">Grade</div>
        <select id="studylockGrade" class="settings-input">${Array.from({length:12},(_,i)=>`<option value="${i+1}" ${profile.grade===i+1?'selected':''}>Grade ${i+1}</option>`).join('')}</select>
      </div>
      <div class="settings-row" style="display:block;">
        <div class="settings-row-label">School term</div>
        <select id="studylockTerm" class="settings-input">${[1,2,3,4].map(t=>`<option value="${t}" ${profile.term===t?'selected':''}>Term ${t}</option>`).join('')}</select>
      </div>
      <button type="button" class="settings-btn primary" id="studylockSaveProfile" style="width:100%;margin-top:10px;">Save student profile</button>
      <div class="settings-hint">The built-in offline study map is strongest for Term 3. Downloaded Cyber Pulse library packs can extend the local knowledge base.</div>`;
    parent.appendChild(section);
    section.querySelector('#studylockSaveProfile')?.addEventListener('click', () => saveProfile({
      name: section.querySelector('#studylockProfileName')?.value || '',
      grade: Number(section.querySelector('#studylockGrade')?.value || 10),
      term: Number(section.querySelector('#studylockTerm')?.value || 3)
    }));
  }

  function timeValue(minute) {
    const h = Math.floor(minute / 60).toString().padStart(2,'0');
    const m = (minute % 60).toString().padStart(2,'0');
    return `${h}:${m}`;
  }

  function parseTime(value) {
    const parts = String(value || '').split(':').map(Number);
    return parts.length === 2 && parts.every(Number.isFinite) ? Math.min(1439, Math.max(0, parts[0]*60+parts[1])) : 0;
  }

  function ensureScheduleUi() {
    if (document.getElementById('studylockParentScheduleSection')) return;
    const parent = curriculumContainer();
    if (!parent) return;
    const schedule = loadSchedule();
    const section = document.createElement('div');
    section.className = 'settings-section';
    section.id = 'studylockParentScheduleSection';
    section.innerHTML = `
      <div class="settings-label">Parent Auto Study schedule</div>
      <div class="settings-row"><div><div class="settings-row-label">Auto Study</div><div class="settings-row-sub">Starts every day and cannot be paused by the student.</div></div><input id="studylockAutoEnabled" type="checkbox" ${schedule.enabled?'checked':''}></div>
      <div class="settings-row" style="display:block;"><div class="settings-row-label">Starts every day</div><input id="studylockAutoStart" type="time" class="settings-input" value="${timeValue(schedule.startMinute)}"></div>
      <div class="settings-row" style="display:block;"><div class="settings-row-label">Ends every day</div><input id="studylockAutoEnd" type="time" class="settings-input" value="${timeValue(schedule.endMinute)}"></div>
      <div class="settings-row" style="display:block;"><div class="settings-row-label">Default study time</div><select id="studylockAutoMinutes" class="settings-input">${[25,45,60,90,120,180,240,300].map(v=>`<option value="${v}" ${schedule.defaultMinutes===v?'selected':''}>${v} minutes</option>`).join('')}</select></div>
      <div class="settings-row" style="display:block;"><div class="settings-row-label">Parent password</div><input id="studylockSchedulePassword" type="password" class="settings-input" autocomplete="current-password" placeholder="Required to change schedule"></div>
      <button type="button" class="settings-btn primary" id="studylockSaveSchedule" style="width:100%;margin-top:10px;">Apply parent schedule</button>
      <div id="studylockScheduleStatus" class="settings-hint"></div>`;
    parent.appendChild(section);
    section.querySelector('#studylockSaveSchedule')?.addEventListener('click', () => {
      const password = section.querySelector('#studylockSchedulePassword')?.value || '';
      let allowed = false;
      try { allowed = !!native?.verifyParentPassword(password); } catch (_) {}
      if (!allowed) {
        window.StudyLockNativeHooks?.showToast?.('Parent password required to change Auto Study.');
        return;
      }
      storeSchedule({
        enabled: !!section.querySelector('#studylockAutoEnabled')?.checked,
        startMinute: parseTime(section.querySelector('#studylockAutoStart')?.value),
        endMinute: parseTime(section.querySelector('#studylockAutoEnd')?.value),
        defaultMinutes: Number(section.querySelector('#studylockAutoMinutes')?.value || 60)
      });
      section.querySelector('#studylockSchedulePassword').value = '';
    });
    refreshScheduleUi();
  }

  function refreshScheduleUi() {
    const status = document.getElementById('studylockScheduleStatus');
    if (!status) return;
    const s = loadSchedule();
    status.textContent = s.enabled ? `Active daily · ${timeValue(s.startMinute)}–${timeValue(s.endMinute)} · default ${s.defaultMinutes} min` : 'Auto Study is off.';
  }

  function ensureSignupGrade() {
    const form = document.getElementById('authSignup');
    const nameInput = document.getElementById('signupName');
    if (!form || !nameInput || document.getElementById('signupGrade')) return;
    const grade = document.createElement('select');
    grade.id = 'signupGrade';
    grade.className = nameInput.className || 'settings-input';
    grade.style.marginTop = '8px';
    grade.innerHTML = Array.from({length:12},(_,i)=>`<option value="${i+1}" ${i+1===10?'selected':''}>Grade ${i+1}</option>`).join('');
    nameInput.insertAdjacentElement('afterend', grade);
    const term = document.createElement('select');
    term.id = 'signupTerm';
    term.className = grade.className;
    term.style.marginTop = '8px';
    term.innerHTML = [1,2,3,4].map(t=>`<option value="${t}" ${t===3?'selected':''}>Term ${t}</option>`).join('');
    grade.insertAdjacentElement('afterend', term);
    document.getElementById('signupSubmitBtn')?.addEventListener('click', () => {
      saveProfile({ name: nameInput.value, grade: Number(grade.value), term: Number(term.value) }, false);
    }, true);
  }

  function taskForToday() {
    const profile = loadProfile();
    const subjects = gradeSubjects(profile.grade);
    const names = Object.keys(subjects);
    const now = new Date();
    const dayIndex = Math.floor(Date.UTC(now.getFullYear(),now.getMonth(),now.getDate())/86400000);
    const subject = names[Math.abs(dayIndex) % names.length];
    const topics = subjects[subject];
    const topic = topics[Math.abs(dayIndex + profile.grade * 13) % topics.length];
    return { profile, subject, title: topic[0], summary: topic[1], formulas: FORMULAS[subject] || [] };
  }

  function renderDailyTask() {
    const hero = document.getElementById('hero');
    if (!hero) return;
    let card = document.getElementById('studylockDailyTask');
    if (!card) {
      card = document.createElement('div');
      card.className = 'glass';
      card.id = 'studylockDailyTask';
      card.style.padding = '16px';
      card.style.marginTop = '12px';
      hero.insertAdjacentElement('afterend', card);
    }
    const task = taskForToday();
    const formula = task.formulas[0] ? `<div style="margin-top:8px;font-family:var(--mono-font);font-size:11.5px;color:var(--amber);">Formula reference: ${task.formulas[0]}</div>` : '';
    card.innerHTML = `<div style="font-size:11px;font-weight:900;color:var(--amber);letter-spacing:.08em;">TODAY'S TERM ${task.profile.term} TASK · GRADE ${task.profile.grade}</div><div style="font-size:18px;font-weight:900;margin-top:5px;">${task.subject}: ${task.title}</div><div style="font-size:12.5px;color:var(--text-1);line-height:1.5;margin-top:6px;">${task.summary}</div>${formula}`;
  }

  function buildOfflineQuestions() {
    const task = taskForToday();
    const subjects = gradeSubjects(task.profile.grade);
    const names = Object.keys(subjects);
    const questions = [];
    for (let i = 0; i < 5; i++) {
      const subject = names[(names.indexOf(task.subject) + i) % names.length];
      const topic = subjects[subject][i % subjects[subject].length];
      const distractors = names.filter(name => name !== subject).slice(0,3).map(name => `${name}: ${subjects[name][0][0]}`);
      while (distractors.length < 3) distractors.push('Unrelated topic');
      const options = [`${subject}: ${topic[0]}`, ...distractors];
      questions.push({
        question: `Which Grade ${task.profile.grade} Term ${task.profile.term} study item matches: ${topic[1]}`,
        options,
        correctIndex: 0,
        explain: `${topic[0]} is the matching ${subject} study topic.`
      });
    }
    return questions;
  }

  function startOfflineCurriculumQuiz() {
    try {
      quizQuestions = buildOfflineQuestions();
      quizCurrentIndex = 0;
      quizScore = 0;
      renderQuizQuestion();
      showQuizScreen('active');
      localStorage.setItem(QUIZ_COUNT_KEY, String((Number(localStorage.getItem(QUIZ_COUNT_KEY)) || 0) + 1));
      syncMetrics();
      window.StudyLockNativeHooks?.showToast?.(`Offline Grade ${loadProfile().grade} Term ${loadProfile().term} quiz started.`);
    } catch (error) {
      console.error(error);
      window.StudyLockNativeHooks?.showToast?.('Could not start the built-in offline curriculum quiz.');
    }
  }

  document.addEventListener('click', event => {
    const target = event.target instanceof Element ? event.target : null;
    if (!target?.closest('#offlineExitQuizBtn')) return;
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    startOfflineCurriculumQuiz();
  }, true);

  function offlineCurriculumAnswer(question) {
    const q = String(question || '').toLowerCase().trim();
    if (!q) return '';
    const profile = loadProfile();
    const subjects = gradeSubjects(profile.grade);
    let best = null;
    let bestScore = 0;
    Object.entries(subjects).forEach(([subject, topics]) => {
      topics.forEach(([title, summary]) => {
        const words = `${subject} ${title} ${summary}`.toLowerCase().split(/[^a-z0-9]+/).filter(w => w.length > 3);
        const score = words.reduce((total, word) => total + (q.includes(word) ? 1 : 0), 0);
        if (score > bestScore) { bestScore = score; best = {subject,title,summary}; }
      });
    });
    if (!best || bestScore < 1) return '';
    const formula = (FORMULAS[best.subject] || []).find(item => q.split(/\s+/).some(word => word.length > 3 && item.toLowerCase().includes(word))) || (FORMULAS[best.subject] || [])[0] || '';
    return `Offline StudyLock reference · Grade ${profile.grade} · Term ${profile.term}\n\n${best.subject} — ${best.title}\n${best.summary}${formula ? `\n\nUseful formula/reference: ${formula}` : ''}\n\nThis answer was generated from the built-in StudyLock curriculum map and installed offline libraries.`;
  }

  const originalNativeAI = typeof window.studyLockNativeAI === 'function' ? window.studyLockNativeAI.bind(window) : null;
  if (originalNativeAI) {
    window.studyLockNativeAI = function studyLockCurriculumAI(body, overrideApiKey) {
      localStorage.setItem(AI_USAGE_KEY, String((Number(localStorage.getItem(AI_USAGE_KEY)) || 0) + 1));
      syncMetrics();
      if (!navigator.onLine) {
        let latest = '';
        try {
          const messages = body?.messages || [];
          for (let i = messages.length - 1; i >= 0; i--) {
            if (messages[i]?.role === 'user') { latest = messages[i]?.content || ''; break; }
          }
        } catch (_) {}
        const local = offlineCurriculumAnswer(latest);
        if (local) return Promise.resolve(local);
      }
      return originalNativeAI(body, overrideApiKey);
    };
  }

  function syncMetrics() {
    const profile = loadProfile();
    let todayMinutes = 0;
    try {
      const raw = document.getElementById('statToday')?.textContent || '0';
      todayMinutes = parseInt(raw, 10) || 0;
    } catch (_) {}
    try {
      native?.syncState(JSON.stringify({
        profileName: profile.name,
        grade: profile.grade,
        term: profile.term,
        aiUsageCount: Number(localStorage.getItem(AI_USAGE_KEY)) || 0,
        quizCount: Number(localStorage.getItem(QUIZ_COUNT_KEY)) || 0,
        todayMinutes,
        curriculumPack: 'built-in-term3-v1',
        curriculumOfflineReady: true,
        clientUpdatedAt: new Date().toISOString()
      }));
    } catch (_) {}
  }

  function applyRemoteControl(data) {
    if (!data || typeof data !== 'object') return;
    if (data.schedule) {
      const s = data.schedule;
      storeSchedule({
        enabled: !!s.enabled,
        startMinute: Number(s.startMinute ?? 18*60),
        endMinute: Number(s.endMinute ?? 20*60),
        defaultMinutes: Number(s.defaultMinutes ?? 60)
      }, false);
    }
    const startId = String(data.startRequestId || '');
    if (startId && startId !== localStorage.getItem(LAST_REMOTE_START)) {
      localStorage.setItem(LAST_REMOTE_START, startId);
      window.__studyLockTrustedParentCommand = true;
      try {
        const mins = Math.min(300, Math.max(25, Number(data.startRequestMinutes) || loadSchedule().defaultMinutes));
        chooseMinutes(mins);
        if (!focusActive() && typeof startSession === 'function') startSession();
        window.StudyLockNativeHooks?.showToast?.('Study session started from StudyLock Control.');
      } finally { setTimeout(() => { window.__studyLockTrustedParentCommand = false; }, 100); }
    }
    const endId = String(data.endRequestId || '');
    if (endId && endId !== localStorage.getItem(LAST_REMOTE_END)) {
      localStorage.setItem(LAST_REMOTE_END, endId);
      window.__studyLockTrustedParentCommand = true;
      try { if (focusActive() && typeof endSessionEarly === 'function') endSessionEarly(); }
      finally { setTimeout(() => { window.__studyLockTrustedParentCommand = false; }, 100); }
    }
    maybeApplySchedule();
  }

  const hooks = window.StudyLockNativeHooks || (window.StudyLockNativeHooks = {});
  const previousRemote = hooks.onRemoteControl;
  hooks.onRemoteControl = function onRemoteControl(raw) {
    if (typeof previousRemote === 'function') { try { previousRemote(raw); } catch (_) {} }
    try { applyRemoteControl(typeof raw === 'string' ? JSON.parse(raw) : raw); } catch (_) {}
  };

  function init() {
    ensureSignupGrade();
    ensureProfileUi();
    ensureScheduleUi();
    renderDailyTask();
    removePauseControls();
    maybeApplySchedule();
    syncMetrics();
    try {
      const s = loadSchedule();
      native?.setAutoStudySchedule(s.enabled, s.startMinute, s.endMinute, s.defaultMinutes);
    } catch (_) {}
  }

  const observer = new MutationObserver(() => {
    removePauseControls();
    ensureSignupGrade();
    ensureProfileUi();
    ensureScheduleUi();
    if (!document.getElementById('studylockDailyTask')) renderDailyTask();
  });
  observer.observe(document.body, {childList:true,subtree:true});

  init();
  setTimeout(init, 400);
  setTimeout(init, 1200);
  setInterval(() => { maybeApplySchedule(); syncMetrics(); }, 30000);

  window.StudyLockTerm3System = {
    getProfile: loadProfile,
    getSchedule: loadSchedule,
    isEnforcedNow: withinSchedule,
    dailyTask: taskForToday,
    startOfflineQuiz: startOfflineCurriculumQuiz,
    offlineAnswer: offlineCurriculumAnswer,
    applyRemoteControl
  };
})();
