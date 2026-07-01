import type {ReactNode} from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Heading from '@theme/Heading';

import styles from './index.module.css';
import Favicon from '@site/static/assets/favicon.svg';
import Link from "@docusaurus/Link";
import DownloadButton from "@site/src/components/DownloadButton";

export default function Home(): ReactNode {
    const {siteConfig} = useDocusaurusContext();
    return (
        <div className={styles.index}>
            <main className="container">
                <Favicon className={styles.logo}/>
                <Heading as="h1" className="logotype">
                    {siteConfig.title}
                </Heading>
                <p className={styles.subtitle}>{siteConfig.tagline}</p>
                <div className={styles.buttons}>
                    <DownloadButton/>
                    <Link href="../../docs/configuration">
                        <button className="button button--primary">View the documentation</button>
                    </Link>
                    <a href={`https://github.com/${siteConfig.organizationName}/${siteConfig.projectName}`}>
                        <button className="button button--primary button--outline">View the source code</button>
                    </a>
                </div>
            </main>
            <footer>
                A work of <a href="https://lmnv.net">Luminova</a>.<br/>
                Not an official Minecraft service. Not approved by or associated with Mojang or Microsoft.
            </footer>
        </div>
    );
}
