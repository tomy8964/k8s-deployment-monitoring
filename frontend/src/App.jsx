import React from 'react';
import {Outlet, Link} from 'react-router-dom';
import './App.css';

function App() {
    return (
        <div className="app-container">
            <header className="header">
                <Link to="/" className="header-link">
                    <h1 className="header-title">ham Monitor</h1>
                    <h1 className="header-title right-align">함건욱</h1>
                </Link>
            </header>

            <main className="main">
                <div className="content-wrapper">
                    <Outlet/>
                </div>
            </main>
        </div>
    );
}

export default App;