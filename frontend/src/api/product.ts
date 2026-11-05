/** product-service */
import { http } from './http'
import type { Brand, Category, HomeData, PageResult, SearchQuery, Spu, SpuCard } from '@/types/domain'

export const productApi = {
  home: () => http.get<HomeData>('/home'),
  recommend: (page: number, size = 10) => http.get<PageResult<SpuCard>>('/home/recommend', { page, size }),
  categoryTree: () => http.get<Category[]>('/categories/tree'),
  search: (q: SearchQuery) => http.get<PageResult<SpuCard> & { brands: Brand[] }>('/products', q),
  detail: (id: string) => http.get<Spu & { favorited: boolean }>(`/products/${id}`),
  toggleFavorite: (id: string) => http.post<boolean>(`/products/${id}/favorite`),
  favorites: () => http.get<SpuCard[]>('/favorites'),
}
