<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { Category } from '@/types/domain'
import { adminApi } from '@/api/admin'
import { ApiError } from '@/api/http'
import { toast } from '@/utils/toast'
import { useAppStore } from '@/stores/app'
import Icon from '@/components/common/Icon.vue'

const app = useAppStore()
const tree = ref<Category[]>([])
const open = ref<Set<number>>(new Set())
async function load() {
  tree.value = await adminApi.categories()
}
onMounted(async () => {
  await load()
  if (tree.value[0]) open.value.add(tree.value[0].id)
})
function flip(id: number) {
  const s = new Set(open.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  open.value = s
}
async function run(fn: () => Promise<unknown>, ok: string) {
  try {
    await fn()
    toast.success(ok)
    await load()
    app.loadCategories(true)
  } catch (e) {
    toast.error((e as ApiError).message)
  }
}
function add(parent: Category | null) {
  const name = prompt(parent ? `在「${parent.name}」下新增子类目` : '新增一级类目')?.trim()
  if (!name) return
  if (parent) open.value = new Set(open.value).add(parent.id)
  run(() => adminApi.addCategory({ parentId: parent?.id ?? 0, name }), '已新增')
}
function rename(c: Category) {
  const name = prompt('重命名类目', c.name)?.trim()
  if (name && name !== c.name) run(() => adminApi.renameCategory(c.id, name), '已重命名')
}
function del(c: Category) {
  if (confirm(`删除类目「${c.name}」？`)) run(() => adminApi.deleteCategory(c.id), '已删除')
}
const level = ['一级', '二级', '三级']
</script>

<template>
  <div class="adm-page">
    <div class="adm-title">
      <h2>类目树</h2><span class="sub">邻接表 parent_id 存储，查询时组装三级树；有子类目或商品时禁止删除</span>
      <div class="right"><button class="btn btn-primary btn-sm btn-square" @click="add(null)"><Icon name="plus" :size="14" /> 一级类目</button></div>
    </div>
    <div class="adm-card tree">
      <template v-for="c1 in tree" :key="c1.id">
        <div class="node l1">
          <button class="fold" @click="flip(c1.id)"><Icon :name="open.has(c1.id) ? 'chevron-down' : 'chevron-right'" :size="14" /></button>
          <b>{{ c1.name }}</b><span class="tag tag-gray">{{ level[0] }}</span><span class="muted id">id={{ c1.id }}</span>
          <span class="ops"><button class="op" @click="add(c1)">+ 子类目</button><button class="op" @click="rename(c1)">重命名</button><button class="op danger" @click="del(c1)">删除</button></span>
        </div>
        <template v-if="open.has(c1.id)">
          <template v-for="c2 in c1.children ?? []" :key="c2.id">
            <div class="node l2">
              <span class="name">{{ c2.name }}</span><span class="tag tag-gray">{{ level[1] }}</span><span class="muted id">parent={{ c1.id }}</span>
              <span class="ops"><button class="op" @click="add(c2)">+ 子类目</button><button class="op" @click="rename(c2)">重命名</button><button class="op danger" @click="del(c2)">删除</button></span>
            </div>
            <div class="leaves">
              <span v-for="c3 in c2.children ?? []" :key="c3.id" class="leaf">
                {{ c3.name }}
                <button title="重命名" @click="rename(c3)"><Icon name="edit" :size="12" /></button>
                <button title="删除" @click="del(c3)"><Icon name="close" :size="12" /></button>
              </span>
            </div>
          </template>
        </template>
      </template>
    </div>
  </div>
</template>

<style scoped>
.tree {
  padding: 8px 18px;
}
.node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid var(--c-border);
  font-size: 13px;
}
.l2 {
  padding-left: 30px;
  border-bottom: none;
  padding-bottom: 4px;
}
.fold {
  border: none;
  background: none;
  padding: 0;
  color: var(--c-text-3);
  display: flex;
}
.id {
  font-size: 12px;
}
.ops {
  margin-left: auto;
}
.leaves {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 4px 0 10px 54px;
  border-bottom: 1px dashed var(--c-border);
}
.leaf {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  background: #f5f6f8;
  border-radius: 4px;
  padding: 3px 4px 3px 10px;
}
.leaf button {
  border: none;
  background: none;
  padding: 0 2px;
  color: var(--c-text-4);
  display: flex;
}
.leaf button:hover {
  color: var(--c-primary);
}
</style>
