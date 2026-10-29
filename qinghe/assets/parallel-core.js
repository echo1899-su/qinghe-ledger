(function (root, factory) {
  const api = factory();
  if (typeof module === 'object') module.exports = api;
  else root.Parallel = api;
})(typeof globalThis === 'undefined' ? this : globalThis, function () {
  'use strict';
  const LIMIT = 99999999999;
  const MAX_PLANS = 20;
  const text = (value, length) => typeof value === 'string' && value.length <= length;
  const integer = (value, min = 0, max = Number.MAX_SAFE_INTEGER) =>
    Number.isSafeInteger(value) && value >= min && value <= max;
  const monthValid = month => /^(19\d{2}|20\d{2}|21\d{2}|2200)-(0[1-9]|1[0-2])$/.test(month);
  function dateValid(date, month) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !date.startsWith(month + '-')) return false;
    const [y, m, d] = date.split('-').map(Number), parsed = new Date(y, m - 1, d);
    return parsed.getFullYear() === y && parsed.getMonth() === m - 1 && parsed.getDate() === d;
  }
  function assert(condition, message) { if (!condition) throw Error(message); }
  function unique(items, label) {
    const ids = new Set();
    for (const item of items) {
      assert(item && text(item.id, 100) && item.id && !ids.has(item.id), label + '编号重复或无效');
      ids.add(item.id);
    }
    return ids;
  }

  // Snapshots deliberately have no live references: editing a real record cannot rewrite an old experiment.
  function validatePlan(plan) {
    assert(plan && text(plan.id, 100) && plan.id && text(plan.name, 60) && plan.name.trim(), '方案名称或编号无效');
    assert(monthValid(plan.month) && text(plan.scopeId, 100) && plan.scopeId && text(plan.scopeName, 60), '方案月份或账本无效');
    assert(integer(plan.created) && integer(plan.updated) && integer(plan.income), '方案收入或时间无效');
    assert(Array.isArray(plan.baseline) && plan.baseline.length <= 100000, '方案账目快照无效');
    assert(Array.isArray(plan.adjustments) && plan.adjustments.length <= plan.baseline.length, '方案调整无效');
    assert(Array.isArray(plan.additions) && plan.additions.length <= 100, '额外安排最多 100 项');
    const ids = unique(plan.baseline, '快照');
    let total = 0;
    for (const record of plan.baseline) {
      assert(integer(record.amount, 1, LIMIT) && dateValid(record.date, plan.month), '快照金额或日期无效');
      assert(text(record.note, 2000) && text(record.categoryName, 60) && text(record.bookName, 60), '快照说明无效');
      total += record.amount;
    }
    assert(integer(total), '方案总额超出支持范围');
    unique(plan.adjustments, '调整');
    for (const item of plan.adjustments) assert(ids.has(item.id) && integer(item.amount, 0, LIMIT), '调整金额或关联账目无效');
    unique(plan.additions, '额外安排');
    for (const item of plan.additions) assert(text(item.name, 60) && item.name.trim() && integer(item.amount, 1, LIMIT), '额外安排名称或金额无效');
    const result = calculate(plan);
    assert(integer(result.projectedExpense), '方案总额超出支持范围');
    return plan;
  }

  function validatePlans(plans) {
    assert(Array.isArray(plans) && plans.length <= MAX_PLANS, '生活平行线最多保存 20 个方案');
    unique(plans, '方案');
    plans.forEach(validatePlan);
    return plans;
  }

  function snapshot(state, { id, name, month, book = 'all', now = Date.now() }) {
    assert(monthValid(month), '请选择有效月份');
    assert(book === 'all' || state.books.some(item => item.id === book), '所选账本不存在');
    const categories = new Map(state.categories.map(item => [item.id, item.name]));
    const books = new Map(state.books.map(item => [item.id, item.name]));
    const records = state.transactions.filter(record => record.date.startsWith(month) && (book === 'all' || record.book === book));
    const baseline = records.filter(record => record.type === 'expense').map(record => ({
      id: record.id, date: record.date, amount: record.amount, note: record.note,
      categoryName: categories.get(record.category) || '其他支出', bookName: books.get(record.book) || '原账本'
    })).sort((a, b) => b.date.localeCompare(a.date) || a.id.localeCompare(b.id));
    return validatePlan({ id, name: name.trim(), month, scopeId: book,
      scopeName: book === 'all' ? '全部账本' : books.get(book), created: now, updated: now,
      income: records.filter(record => record.type === 'income').reduce((sum, record) => sum + record.amount, 0),
      baseline, adjustments: [], additions: [] });
  }

  function calculate(plan) {
    const amounts = new Map(plan.adjustments.map(item => [item.id, item.amount]));
    let originalExpense = 0, projectedExpense = 0;
    for (const record of plan.baseline) {
      originalExpense += record.amount;
      projectedExpense += amounts.has(record.id) ? amounts.get(record.id) : record.amount;
    }
    projectedExpense += plan.additions.reduce((sum, item) => sum + item.amount, 0);
    return { originalExpense, projectedExpense, originalNet: plan.income - originalExpense,
      projectedNet: plan.income - projectedExpense, difference: originalExpense - projectedExpense };
  }

  // Replacing an existing adjustment keeps each original purchase counted exactly once.
  function adjust(plan, recordIds, amount) {
    assert(integer(amount, 0, LIMIT), '请输入有效金额');
    const originals = new Map(plan.baseline.map(record => [record.id, record.amount]));
    const edits = new Map(plan.adjustments.map(item => [item.id, item.amount]));
    for (const id of new Set(recordIds)) {
      assert(originals.has(id), '所选账目不在此方案中');
      if (amount === originals.get(id)) edits.delete(id); else edits.set(id, amount);
    }
    return validatePlan({ ...plan, adjustments: [...edits].map(([id, value]) => ({ id, amount: value })) });
  }

  return { LIMIT, MAX_PLANS, snapshot, calculate, validatePlan, validatePlans, adjust };
});
