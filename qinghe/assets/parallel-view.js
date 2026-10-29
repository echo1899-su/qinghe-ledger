(function (root) {
  'use strict';

  function create({ state, period, scope, money, esc, act, icon, modal, close, confirmModal,
    commit, render, navigate, toast, uid, amount }) {
    let draft = null, savedText = '', isNew = false, changesShown = 5;
    let selected = new Set(), pickerIndex = [], query = '', pickerShown = 40;
    const plans = () => state().scenarios || [];
    const dirty = () => !!draft && (isNew || JSON.stringify(draft) !== savedText);
    const context = plan => `${plan.month} · ${plan.scopeName}`;
    const errorBox = '<p id="formError" class="form-error" role="alert"></p>';
    const button = (action, label, data = '', cls = 'button secondary') => act('parallel-' + action, label, cls, data);
    const sumText = result => state().settings.hideAmounts ? '比较金额已隐藏' :
      result.difference > 0 ? `比原来多留 ¥${money(result.difference)}` :
      result.difference < 0 ? `比原来多安排 ¥${money(-result.difference)}` : '和原来的支出相同';

    function discard() { draft = null; savedText = ''; isNew = false; }
    function leave(next) {
      if (dirty()) confirmModal('离开这份安排？', '这次调整还没有保存。确认离开会放弃未保存的改动，已保存的方案和真实账目都会保留。', () => { discard(); close(); next(); });
      else { discard(); next(); }
    }
    function refresh(top = false) { render(); if (top) window.scrollTo({ top: 0, behavior: 'instant' }); }
    function open() { discard(); navigate('parallel'); }
    function back() {
      if (draft) leave(() => refresh(true));
      else navigate('stats');
    }

    function entry() {
      const recent = plans().filter(plan => plan.month === period() && plan.scopeId === scope()).sort((a, b) => b.updated - a.updated)[0];
      const detail = recent ? `<span class="parallel-entry-plan">${esc(recent.name)}</span><span class="parallel-entry-caption">${esc(sumText(Parallel.calculate(recent)))} · 保存时的方案</span>` : '<span class="parallel-entry-caption">如果换一种花法，这个月会有什么不同？</span>';
      return act('parallel-open', `<span class="parallel-entry-art" aria-hidden="true"><i></i><i></i></span><span class="parallel-entry-copy"><strong>生活平行线</strong>${detail}<span class="parallel-entry-link">${recent ? '继续看看' : '试着安排一下'} <span aria-hidden="true">↗</span></span></span>`, 'card parallel-entry');
    }

    function header(title, subtitle) {
      return `<div class="parallel-header">${button('back', '‹ 返回', '', 'text-btn')}<span>生活平行线</span><span class="parallel-local">本机保存</span></div><div class="parallel-title"><h1>${esc(title)}</h1><p>${esc(subtitle)}</p></div>`;
    }

    function hub() {
      const cards = [...plans()].sort((a, b) => b.updated - a.updated).map(plan => {
        const result = Parallel.calculate(plan);
        return `<article class="card parallel-saved">${button('load', `<span class="parallel-saved-context">${esc(context(plan))}</span><strong>${esc(plan.name)}</strong><span class="parallel-saved-result">${esc(sumText(result))}</span><span class="parallel-entry-link">打开这份安排 →</span>`, `data-id="${esc(plan.id)}"`, 'parallel-saved-open')}${button('delete', '删除方案', `data-id="${esc(plan.id)}"`, 'parallel-delete')}</article>`;
      }).join('');
      return header('生活，还可以这样过', '拿自己的账目，试一试另一种安排。') +
        `<section class="card parallel-welcome"><span class="parallel-overline">一张可以涂改的生活草稿</span><h2>保留喜欢的，<br>试着调整一点。</h2><p>挑几笔消费，换一种花法；也给想做的事留个位置。</p>${button('new', icon('plus') + '新建一份安排', '', 'button')}</section><div class="section-head section"><h2>我的方案</h2><span class="tiny muted">${plans().length} / ${Parallel.MAX_PLANS}</span></div>${cards ? `<div class="parallel-saved-grid">${cards}</div>` : '<p class="parallel-empty">还没有保存的方案。<br>先从一个月的小调整开始吧。</p>'}<p class="parallel-caption">只是一份假设，不会生成、修改或删除真实账目。</p>`;
    }

    function editor() {
      const result = Parallel.calculate(draft);
      const baseline = new Map(draft.baseline.map(record => [record.id, record]));
      const edits = draft.adjustments.slice(0, changesShown).map(edit => {
        const record = baseline.get(edit.id);
        return `<div class="parallel-change"><div class="parallel-change-copy"><strong>${esc(record.note || record.categoryName)}</strong><small>${esc(record.date)} · ${esc(record.categoryName)}</small><span>¥${money(record.amount)} <span aria-hidden="true">→</span> ${edit.amount ? '¥' + money(edit.amount) : '这次先不买'}</span></div><div class="parallel-change-actions">${button('edit', '修改', `data-id="${esc(edit.id)}"`, '')}${button('undo', '撤销', `data-id="${esc(edit.id)}"`, '')}</div></div>`;
      }).join('');
      const additions = draft.additions.map(item => `<div class="parallel-change"><div class="parallel-change-copy"><strong>${esc(item.name)}</strong><small>想做的事 · 额外安排</small><span>¥${money(item.amount)}</span></div><div class="parallel-change-actions">${button('addition', '修改', `data-id="${esc(item.id)}"`, '')}${button('remove-addition', '撤销', `data-id="${esc(item.id)}"`, '')}</div></div>`).join('');
      return header(draft.name, context(draft)) + `<div class="parallel-editor-meta"><span>${draft.baseline.length} 笔支出快照 · 收入 ¥${money(draft.income)}</span>${button('rename', '改个名字', '', 'text-btn')}</div><section class="parallel-comparison" aria-label="两种安排的比较"><div class="card parallel-before"><span class="parallel-overline">原来的这个月</span><small>支出</small><strong>¥${money(result.originalExpense)}</strong><span class="parallel-net">结余 ¥${money(result.originalNet)}</span></div><div class="card parallel-after"><span class="parallel-overline">我的另一种安排</span><small>支出</small><strong>¥${money(result.projectedExpense)}</strong><span class="parallel-net">结余 ¥${money(result.projectedNet)}</span></div></section><p class="parallel-difference" aria-live="polite">${esc(sumText(result))}</p><div class="parallel-toolbar">${button('pick', icon('list') + '挑几笔来调整')}${button('addition', icon('plus') + '加件想做的事')}</div><section class="card parallel-changes"><div class="row between"><h2>我的小调整</h2><span class="tiny muted">${draft.adjustments.length + draft.additions.length} 项</span></div>${edits || additions ? edits + additions : '<p class="parallel-empty">比如换几次出行方式，<br>或给周末的电影留一张票。</p>'}${draft.adjustments.length > changesShown ? button('more-changes', '展开更多调整', '', 'parallel-open-more') : ''}</section><p class="parallel-caption">基于创建时的账目快照，新记账目不会自动加入。结果仅按你的假设计算，真实账目保持原样。</p><div class="parallel-save-row"><span>${dirty() ? '有尚未保存的安排' : '这份安排已保存'}</span>${button('save', icon('check') + '保存方案', '', 'button')}</div>`;
    }

    function page() { return `<div class="parallel-screen">${draft ? editor() : hub()}</div>`; }

    function newForm() {
      if (plans().length >= Parallel.MAX_PLANS) { toast('最多保存 20 个方案，可以先删除不再需要的方案'); return; }
      const options = [{ id: 'all', name: '全部账本' }, ...state().books].map(book => `<option value="${esc(book.id)}" ${book.id === scope() ? 'selected' : ''}>${esc(book.name)}</option>`).join('');
      modal('新建一份安排', `<form id="parallel-new-form"><label for="parallel-name">给它起个名字</label><input id="parallel-name" name="name" value="换一种花法" maxlength="60" required><label for="parallel-month">拿哪个月试一试</label><input id="parallel-month" type="month" name="month" value="${esc(period())}" min="1900-01" max="2200-12" required><label>选择账本</label><select name="book">${options}</select><p class="note-box">复制所选月份的支出和收入总额，作为这份方案的起点。不会复制图片，也不会修改原账目。</p>${errorBox}<div class="sheet-actions"><button type="submit" class="button">开始安排</button></div></form>`, { name: 'parallel-new' });
    }

    function picker(ids = []) {
      selected = new Set(ids); query = ''; pickerShown = 40;
      pickerIndex = draft.baseline.map(record => ({ record, search: [record.note, record.categoryName, record.bookName, money(record.amount), String(record.amount / 100)].join(' ').toLowerCase() }));
      const edit = ids.length === 1 ? draft.adjustments.find(item => item.id === ids[0]) : null;
      modal('挑几笔，换种花法', `<form id="parallel-adjust-form"><input id="parallel-search" type="search" placeholder="搜索用途、分类或金额" aria-label="搜索方案账目" autocomplete="off"><p class="parallel-picker-tip">仅列出这份方案里的支出。一次选多笔时，下面的金额会用于每一笔。</p><div id="parallel-picker-list" class="parallel-picker-list"></div><div class="parallel-selected-row"><span id="parallel-selected" aria-live="polite"></span>${button('clear-selection', '清空选择', '', 'text-btn')}</div><label for="parallel-amount">每笔改为多少钱</label><input id="parallel-amount" name="amount" type="text" inputmode="decimal" placeholder="例如 15.00" value="${edit ? (edit.amount / 100).toFixed(2) : ''}" maxlength="14"><p class="parallel-picker-tip">已调整过的账目会替换原假设，不重复计算。</p>${errorBox}<div class="parallel-picker-actions">${button('skip-selected', '这次先不买')}<button type="submit" class="button">应用新金额</button></div></form>`, { name: 'parallel-picker' });
      drawPicker();
    }

    function drawPicker() {
      const container = document.querySelector('#parallel-picker-list');
      if (!container) return;
      const matches = pickerIndex.filter(item => !query || item.search.includes(query));
      const adjusted = new Map(draft.adjustments.map(item => [item.id, item.amount]));
      container.innerHTML = matches.slice(0, pickerShown).map(({ record }) => `<label class="parallel-pick-row"><input type="checkbox" data-parallel-pick="${esc(record.id)}" ${selected.has(record.id) ? 'checked' : ''}><span><strong>${esc(record.note || record.categoryName)}</strong><small>${esc(record.date)} · ${esc(record.categoryName)}${adjusted.has(record.id) ? ' · 已有调整' : ''}</small></span><b>¥${money(record.amount)}</b></label>`).join('') || '<p class="parallel-empty">没有找到符合条件的支出。</p>';
      if (matches.length > pickerShown) container.innerHTML += button('more-picks', `继续显示 · 共 ${matches.length} 笔`, '', 'parallel-open-more');
      selectionText();
    }
    function selectionText() {
      const node = document.querySelector('#parallel-selected');
      if (node) node.textContent = `已选 ${selected.size} 笔（含其他搜索结果）`;
    }
    function search(value) { query = value.trim().toLowerCase(); pickerShown = 40; drawPicker(); }
    function change(element) {
      const id = element.dataset.parallelPick;
      if (id) { if (element.checked) selected.add(id); else selected.delete(id); selectionText(); }
    }
    function apply(value) {
      if (!selected.size) throw Error('请先勾选至少一笔支出');
      draft = Parallel.adjust(draft, [...selected], value);
      close(); refresh();
    }

    function additionForm(id) {
      const item = draft.additions.find(item => item.id === id);
      modal(item ? '调整这件想做的事' : '加件想做的事', `<form id="parallel-addition-form" data-id="${esc(item?.id || '')}"><label for="parallel-addition-name">想做什么</label><input id="parallel-addition-name" name="name" value="${esc(item?.name || '')}" maxlength="60" placeholder="比如周末看场电影" required><label for="parallel-addition-amount">预计花多少钱</label><input id="parallel-addition-amount" name="amount" inputmode="decimal" maxlength="14" value="${item ? (item.amount / 100).toFixed(2) : ''}" placeholder="例如 45.00" required><p class="note-box">这里只加进假设方案，不会生成真实账目。</p>${errorBox}<div class="sheet-actions"><button type="submit" class="button">放进我的安排</button></div></form>`, { name: 'parallel-addition' });
    }

    function save() {
      const plan = Parallel.validatePlan({ ...draft, updated: Date.now() });
      const success = commit(next => {
        const saved = next.scenarios || [], index = saved.findIndex(item => item.id === plan.id);
        if (index >= 0) saved[index] = plan; else saved.push(plan);
        next.scenarios = Parallel.validatePlans(saved);
      }, '方案已保存，真实账目保持原样', false);
      if (success) { draft = JSON.parse(JSON.stringify(plan)); savedText = JSON.stringify(draft); isNew = false; refresh(); }
    }

    function submit(form) {
      const values = Object.fromEntries(new FormData(form));
      if (form.id === 'parallel-new-form') {
        draft = Parallel.snapshot(state(), { id: uid(), name: values.name, month: values.month, book: values.book });
        savedText = ''; isNew = true; changesShown = 5; close(); refresh(true);
      } else if (form.id === 'parallel-adjust-form') apply(amount(values.amount));
      else if (form.id === 'parallel-addition-form') {
        const value = amount(values.amount); if (!value) throw Error('预计金额需要大于零');
        const item = { id: form.dataset.id || uid(), name: values.name.trim(), amount: value };
        const additions = draft.additions.filter(existing => existing.id !== item.id).concat(item);
        draft = Parallel.validatePlan({ ...draft, additions }); close(); refresh();
      } else if (form.id === 'parallel-rename-form') {
        draft = Parallel.validatePlan({ ...draft, name: values.name.trim() }); close(); refresh();
      }
    }

    function action(action, element) {
      const id = element.dataset.id;
      switch (action) {
        case 'parallel-open': open(); break;
        case 'parallel-back': back(); break;
        case 'parallel-new': newForm(); break;
        case 'parallel-load': {
          const plan = plans().find(item => item.id === id); if (!plan) return;
          draft = JSON.parse(JSON.stringify(plan)); savedText = JSON.stringify(draft); isNew = false; changesShown = 5; refresh(true); break;
        }
        case 'parallel-pick': picker(); break;
        case 'parallel-edit': picker([id]); break;
        case 'parallel-skip-selected': try { apply(0); } catch (error) { document.querySelector('#formError').textContent = error.message; } break;
        case 'parallel-clear-selection': selected.clear(); drawPicker(); break;
        case 'parallel-more-picks': pickerShown += 40; drawPicker(); break;
        case 'parallel-more-changes': changesShown += 10; refresh(); break;
        case 'parallel-undo': draft = { ...draft, adjustments: draft.adjustments.filter(item => item.id !== id) }; refresh(); break;
        case 'parallel-addition': additionForm(id); break;
        case 'parallel-remove-addition': draft = { ...draft, additions: draft.additions.filter(item => item.id !== id) }; refresh(); break;
        case 'parallel-rename': modal('给方案改个名字', `<form id="parallel-rename-form"><label for="parallel-rename">方案名称</label><input id="parallel-rename" name="name" value="${esc(draft.name)}" maxlength="60" required>${errorBox}<div class="sheet-actions"><button type="submit" class="button">确定</button></div></form>`, { name: 'parallel-rename' }); break;
        case 'parallel-save': save(); break;
        case 'parallel-delete': confirmModal('删除这份方案？', '只删除保存的假设方案，真实账目不受影响。', () => {
          if (commit(next => { next.scenarios = (next.scenarios || []).filter(item => item.id !== id); }, '方案已删除')) close();
        }); break;
      }
    }

    return { entry, page, action, submit, change, search, dirty, leave, back };
  }
  root.ParallelView = { create };
})(globalThis);
