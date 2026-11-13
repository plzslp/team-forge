import type { Game, RankTier } from './domain';

type Emblem = {
  silhouette: string;
  face: string;
  highlight: string;
  colors: readonly [string, string, string];
};

export const rankEmblems: Record<Game, Partial<Record<RankTier, Emblem>>> = {
  LOL: {
    IRON: {
      silhouette: 'M2 6 5 9 9 11 12 19 7 16 4 17 2 13ZM22 6 19 9 15 11 12 19 17 16 20 17 22 13ZM9 8l3-1 3 1-3 3Z',
      face: 'M3 8 9 12 11 17 5 13ZM21 8 15 12 13 17 19 13Z',
      highlight: 'm9 8 3-1 3 1-3 1Z',
      colors: ['#554447', '#856c69', '#b8a19b'],
    },
    BRONZE: {
      silhouette: 'm2 9 3 1V4l4 6 3 2 3-2 4-6v6l3-1-3 7-7 5-7-5Z',
      face: 'm5 7 4 5 3 2 3-2 4-5-2 8-5 5-5-5Z',
      highlight: 'm9 11 3 1 3-1-3 6Z',
      colors: ['#75412f', '#b67455', '#efb18c'],
    },
    SILVER: {
      silhouette: 'M2 4 7 9l3 1 2 3 2-3 3-1 5-5-1 7 2-1-4 7-7 4-7-4-4-7 2 1Z',
      face: 'm3 6 5 5 4 5 4-5 5-5-3 9-6 5-6-5Z',
      highlight: 'm12 6 1.3 2.5L16 9l-2.7 1L12 13l-1.3-3L8 9l2.7-.5Z',
      colors: ['#384b62', '#8399ae', '#d6e6ef'],
    },
    GOLD: {
      silhouette: 'm2 9 2 1 1-7 4 7 3 2 3-2 4-7 1 7 2-1-3 7-2 1 2 1-4 1-3 3-3-3-4-1 2-1-2-1Z',
      face: 'm5 6 4 6 3 2 3-2 4-6-2 9-5 5-5-5Z',
      highlight: 'm12 9 2 2-2 4-2-4ZM5 6l4 6-3-2Zm14 0-4 6 3-2Z',
      colors: ['#794b16', '#c38b2b', '#ffe29a'],
    },
    PLATINUM: {
      silhouette: 'm1 9 3 2V2l4 7 4 4 4-4 4-7v9l3-2-4 8-7 5-7-5Z',
      face: 'm4 5 4 6 4 4 4-4 4-6-2 10-6 5-6-5Z',
      highlight: 'm12 8 2.5 3-2.5 3-2.5-3ZM4 5l4 6-3-2Zm16 0-4 6 3-2Z',
      colors: ['#164658', '#2c879e', '#96e1ee'],
    },
    EMERALD: {
      silhouette: 'M1 13 5 10 5 2 8 8l4 4 4-4 3-6v8l4 3-4 3-2 4-5 2-5-2-2-4Z',
      face: 'M5 6 8 11l4 4 4-4 3-5-1 8-6 6-6-6Z',
      highlight: 'm12 9 2 2-2 5-2-5ZM3 13l3 1 2 3-4-2Zm18 0-3 1-2 3 4-2Z',
      colors: ['#124c3a', '#2a9e70', '#9de8be'],
    },
    DIAMOND: {
      silhouette: 'm1 6 4 2L3 3l7 6 2 3 2-3 7-6-2 5 4-2-4 9-3 3-4 3-4-3-3-3Z',
      face: 'm3 7 6 4 3 5 3-5 6-4-4 8-5 5-5-5Z',
      highlight: 'm12 7 2 2-2 3-2-3ZM3 7l6 4-2 1Zm18 0-6 4 2 1Z',
      colors: ['#34396f', '#557ad2', '#a5e5ff'],
    },
    MASTER: {
      silhouette: 'm1 3 5 3 3 5 3 2 3-2 3-5 5-3-2 7 1-1-4 8-6 5-6-5-4-8 1 1Z',
      face: 'm3 5 5 5 4 6 4-6 5-5-4 11-5 4-5-4Z',
      highlight: 'm12 7 2 4-2 3-2-3ZM6 12l5 4 1 4-3-4Zm12 0-5 4-1 4 3-4Z',
      colors: ['#57246f', '#a154c7', '#f0b3ff'],
    },
    GRANDMASTER: {
      silhouette: 'm1 3 4 1 3 6 4 3 4-3 3-6 4-1-3 7 3-1-4 7-4 3-3 3-3-3-4-3-4-7 3 1Z',
      face: 'm4 5 5 7 3 3 3-3 5-7-3 9-5 6-5-6Z',
      highlight: 'm12 8 2.5 2-2.5 4-2.5-4ZM5 12l4 3 3 5-4-3Zm14 0-4 3-3 5 4-3Z',
      colors: ['#721f39', '#d44344', '#ffae76'],
    },
    CHALLENGER: {
      silhouette: 'm1 6 4 2V2l4 7 3 3 3-3 4-7v6l4-2-3 7 2-1-5 6-1 4-4-3-4 3-1-4-5-6 2 1Z',
      face: 'm4 7 5 5 3 4 3-4 5-5-3 8-3 2-2 4-2-4-3-2Z',
      highlight: 'm12 5 1 3 3 1-3 1-1 4-1-4-3-1 3-1ZM1 6l4 2 3 5-3-2Zm22 0-4 2-3 5 3-2Z',
      colors: ['#155481', '#c18d31', '#a6efff'],
    },
  },
  OVERWATCH: {
    BRONZE: {
      silhouette: 'M2 4l10 8L22 4v7L12 21 2 11Z',
      face: 'm3.5 7 8.5 7 8.5-7v3.5L12 19l-8.5-8.5Z',
      highlight: 'm3.5 7 8.5 7 8.5-7-8.5 9Z',
      colors: ['#884424', '#be764a', '#edaf83'],
    },
    SILVER: {
      silhouette: 'M3 3l8 7v11l-4-3v-6L3 8Zm18 0-8 7v11l4-3v-6l4-4Z',
      face: 'M4.5 6 10 11v7l-2-2v-5l-3.5-4Zm15 0L14 11v7l2-2v-5l3.5-4Z',
      highlight: 'm4.5 6 5.5 5-2.5-1Zm15 0L14 11l2.5-1Z',
      colors: ['#5d6876', '#a4b0bb', '#e0e8ed'],
    },
    GOLD: {
      silhouette: 'm2 2 7 6v3l2 2v9l-5-4v-6L2 8Zm20 0-7 6v3l-2 2v9l5-4v-6l4-4ZM9 8h6v4l-3 3-3-3Z',
      face: 'm3.5 5 4 4v3l2 2v5l-2-2v-6l-4-4Zm17 0-4 4v3l-2 2v5l2-2v-6l4-4Z',
      highlight: 'M10 9.5h4v2l-2 2-2-2Z',
      colors: ['#885a18', '#dda42c', '#ffe494'],
    },
    PLATINUM: {
      silhouette: 'm2 2 7 6v3l3 3 3-3V8l7-6v7L12 19 2 9Zm4 13 6 5 6-5v3l-6 5-6-5ZM9 7h6v4l-3 2-3-2Z',
      face: 'm3.5 5 4 4v3l4.5 4 4.5-4V9l4-4v3.5L12 17 3.5 8.5Z',
      highlight: 'M10 8h4v2l-2 1.5-2-1.5Zm-3 9 5 4 5-4v.6l-5 4-5-4Z',
      colors: ['#386379', '#78aec1', '#d4f0f5'],
    },
    EMERALD: {
      silhouette: 'm2 2 6 5v4l4 4 4-4V7l6-5v7L12 19 2 9ZM9 8h6v4l-3 3-3-3ZM5 13l6 6v4l-6-5Zm14 0-6 6v4l6-5Z',
      face: 'm3.5 5 3 3v4l5.5 5 5.5-5V8l3-3v3.5L12 17.5 3.5 8.5ZM6 16l4 4v1l-4-4Zm12 0-4 4v1l4-4Z',
      highlight: 'M10 9h4v2l-2 2-2-2ZM3.5 5l3 3v2Zm17 0-3 3v2Z',
      colors: ['#13553e', '#3cbd8c', '#b6ffde'],
    },
    DIAMOND: {
      silhouette: 'M3 2l6 5v3l3 3 3-3V7l6-5v7l-9 8-9-8Zm-2 9 6 2 4 5v4l-6-5-4-4Zm22 0-6 2-4 5v4l6-5 4-4ZM9 6h6v4l-3 3-3-3Z',
      face: 'm4.5 5 3 3v3l4.5 4 4.5-4V8l3-3v3l-7.5 7-7.5-7ZM3 13l4 2 3 4v1l-4-4Zm18 0-4 2-3 4v1l4-4Z',
      highlight: 'M10 7h4v2l-2 2-2-2Z',
      colors: ['#324e7b', '#749bd5', '#d7ebff'],
    },
    MASTER: {
      silhouette: 'M1 5h5l6 6 6-6h5l-6 7v5l-5 5-5-5v-5Zm11-3 4 4-4 4-4-4ZM4 11l2 2v5l-2-2Zm16 0-2 2v5l2-2Z',
      face: 'M4 6h2l6 6 6-6h2l-5 6v5l-3 3-3-3v-5Z',
      highlight: 'm12 3.5 2.5 2.5L12 8.5 9.5 6ZM4 6h2l4 4-3-2Zm16 0h-2l-4 4 3-2Z',
      colors: ['#2c6656', '#73aa96', '#dcf2e6'],
    },
    GRANDMASTER: {
      silhouette: 'M1 5h6l5 5 5-5h6l-6 7v5l-5 5-5-5v-5ZM8 5h2l2 3 2-3h2l-4 5ZM3 11l3 2v5l-3-3Zm18 0-3 2v5l3-3Z',
      face: 'M4 6h3l5 6 5-6h3l-5 6v5l-3 3-3-3v-5Z',
      highlight: 'M4 6h3l5 6 5-6h3l-4 4-4 4-4-4Z',
      colors: ['#514674', '#9890bf', '#e8e1ff'],
    },
    CHAMPION: {
      silhouette: 'M.5 6h6l5 5.5V15h1v-3.5l5-5.5h6l-3.5 3.5h-2l-3 3V15l3 2.5v2.8l-2.7-2.1L12 22l-3.3-3.8L6 20.3v-2.8L9 15v-2.5l-3-3H4ZM7.5 3l4 3.8v3L7.5 6.3ZM16.5 3l-4 3.8v3l4-3.5ZM4 10.5h2l2 2v2.8l-1.4-1.1v-1.3ZM20 10.5h-2l-2 2v2.8l1.4-1.1v-1.3Z',
      face: 'M2.7 7h3.5l4.4 4.9v3.6l1.4.9 1.4-.9v-3.6L17.8 7h3.5l-1.8 1.6h-2l-3.6 3.6v3.2l2.9 2.4v.8l-1.7-1.4-3.1 3.5-3.1-3.5-1.7 1.4v-.8l2.9-2.4v-3.2L6.5 8.6h-2Z',
      highlight: 'M2.7 7h3.5l4.4 4.9v1L5.8 8H3.9Zm18.6 0h-3.5l-4.4 4.9v1L18.2 8h1.9ZM8.2 4.8l2.6 2.4v1.2L8.2 6Zm7.6 0-2.6 2.4v1.2L15.8 6Z',
      colors: ['#67446e', '#ac82bf', '#f0d9f4'],
    },
  },
};
