import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';
import githubAdmonitions from 'remark-github-admonitions-to-directives'

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
    title: 'Homerun',
    tagline: 'Minecraft server resets, but better. Keep some chunks, or wipe everything completely.',
    favicon: 'assets/favicon.ico',

    // Future flags, see https://docusaurus.io/docs/api/docusaurus-config#future
    future: {
        v4: true, // Improve compatibility with the upcoming Docusaurus v4
    },

    // Set the production url of your site here
    url: 'https://homerun.lmnv.net/',
    // Set the /<baseUrl>/ pathname under which your site is served
    // For GitHub pages deployment, it is often '/<projectName>/'
    baseUrl: '/',

    // GitHub pages deployment config.
    // If you aren't using GitHub pages, you don't need these.
    organizationName: 'team-luminova', // Usually your GitHub org/user name.
    projectName: 'Homerun', // Usually your repo name.

    onBrokenLinks: 'throw',

    // Even if you don't use internationalization, you can use this field to set
    // useful metadata like html lang. For example, if your site is Chinese, you
    // may want to replace "en" with "zh-Hans".
    i18n: {
        defaultLocale: 'en',
        locales: ['en'],
    },

    presets: [
        [
            'classic',
            {
                pages: {
                    beforeDefaultRemarkPlugins: [githubAdmonitions]

                },
                docs: {
                    sidebarPath: './sidebars.ts',
                    editUrl:
                        'https://github.com/team-luminova/Homerun/tree/main/docs/',
                    beforeDefaultRemarkPlugins: [githubAdmonitions]
                },
                theme: {
                    customCss: './src/css/custom.css',
                },
            } satisfies Preset.Options,
        ],
    ],

    themes: ["docusaurus-json-schema-plugin"],

    themeConfig: {
        // Replace with your project's social card
        image: 'assets/social-card.png',
        colorMode: {
            respectPrefersColorScheme: true,
        },
        navbar: {
            title: 'Homerun',
            logo: {
                alt: 'Homerun logo',
                src: 'assets/favicon.svg',
            },
            items: [
                {
                    type: 'docSidebar',
                    sidebarId: 'tutorialSidebar',
                    position: 'left',
                    label: 'Docs',
                },
                {
                    href: 'https://github.com/team-luminova/Homerun',
                    label: 'GitHub',
                    position: 'right',
                },
            ],
        },
        footer: {
            style: 'dark',
            links: [
                {
                    title: 'Docs',
                    items: [
                        {
                            label: 'Configuration',
                            to: '/docs/configuration',
                        },
                        {
                            label: 'Custom messages',
                            to: '/docs/custom-messages',
                        },
                        {
                            label: 'Placeholders',
                            to: '/docs/placeholders',
                        },
                        {
                            label: 'Commands',
                            to: '/docs/commands',
                        },
                    ],
                },
                {
                    title: 'More',
                    items: [
                        {
                            label: 'GitHub',
                            href: 'https://github.com/team-luminova/Homerun',
                        },
                    ],
                },
            ],
            copyright: `Copyright © ${new Date().getFullYear()} Chlod Alejandro, Luminova. Licensed under the Apache License 2.0.<br>
            Not an official Minecraft service. Not approved by or associated with Mojang or Microsoft.`,
        },
        prism: {
            theme: prismThemes.nightOwlLight,
            darkTheme: prismThemes.nightOwl,
        },
    } satisfies Preset.ThemeConfig,
};

export default config;
