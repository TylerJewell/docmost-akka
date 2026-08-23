export interface IContributor {
  id: string;
  name: string;
  avatarUrl?: string;
}

export interface IPageHistory {
  id: string;
  createdAt: string;
  contributors: IContributor[];
  lastUpdatedBy?: IContributor;
  title?: string;
  paragraphs?: string[];
}
