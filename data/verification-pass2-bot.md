# Dataset verification, pass 2 (bot lot)

Scope: every `tier=1` row of `data/transfers.csv` whose `player_id` is NOT
in the pass-2 exclusion list (the 40 most famous players, verified in a
parallel pass). Method per `data/SOURCES.md`: each row checked against at
least one independent public source; only explicitly sourced corrections
were applied, in the same commit. The `tier` column was never touched.

Totals: 88 rows verified, 22 rows fixed (22 `kind` corrections: 21 to
`free`, 1 to `loan`; row 99 additionally had its year fixed to 2021),
0 unverified.

| Row | Claim / evidence | Verdict | Source | Action |
| --- | --- | --- | --- | --- |
| 1 | Fiorentina->Juventus 1990 world record paid transfer ~GBP 8m | confirmed | https://en.wikipedia.org/wiki/Roberto_Baggio | none |
| 2 | Juventus->AC Milan 1995 paid transfer ~GBP 6.8m | confirmed | https://en.wikipedia.org/wiki/Roberto_Baggio | none |
| 3 | AC Milan->Bologna 1997 paid transfer | confirmed | https://en.wikipedia.org/wiki/Roberto_Baggio | none |
| 4 | Bologna->Inter Milan 1998 paid transfer | confirmed | https://en.wikipedia.org/wiki/Roberto_Baggio | none |
| 24 | Chelsea->Real Madrid 2018 paid transfer ~EUR 38.8m | confirmed | https://en.wikipedia.org/wiki/Thibaut_Courtois | none |
| 35 | Palermo->Napoli structured as loan EUR 5m + obligation to buy EUR 12m | fixed | https://en.wikipedia.org/wiki/Edinson_Cavani | fixed: kind=loan |
| 36 | Napoli->PSG 2013 paid transfer ~EUR 64m | confirmed | https://en.wikipedia.org/wiki/Edinson_Cavani | none |
| 37 | PSG contract expired June 2020, joined Man Utd as free agent | fixed | https://bleacherreport.com/articles/2912133-edinson-cavani-signs-manchester-united-contract-on-free-transfer-after-psg-exit | fixed: kind=free |
| 38 | Man Utd contract expired 2022, Valencia signed him as free agent | fixed | https://www.goal.com/en-gb/news/valencia-sign-cavani-free-transfer-after-man-utd-exit/blt208500ccdced1d77 | fixed: kind=free |
| 54 | Sporting Gijon->Real Zaragoza 2003 paid transfer ~EUR 3m | confirmed | https://en.wikipedia.org/wiki/David_Villa | none |
| 55 | Real Zaragoza->Valencia 2005 paid transfer ~EUR 12m | confirmed | https://en.wikipedia.org/wiki/David_Villa | none |
| 56 | Valencia->Barcelona 2010 paid transfer EUR 40m | confirmed | https://en.wikipedia.org/wiki/David_Villa | none |
| 57 | Barcelona->Atletico Madrid 2013 paid transfer EUR 5.1m | confirmed | https://en.wikipedia.org/wiki/David_Villa | none |
| 95 | Sevilla->Barcelona 2008 paid transfer EUR 32.5m | confirmed | https://en.wikipedia.org/wiki/Dani_Alves | none |
| 96 | Barcelona->Juventus 2016 free transfer via contract clause | fixed | https://en.wikipedia.org/wiki/Dani_Alves | fixed: kind=free |
| 97 | Juventus->PSG 2017 signed on a free after Juve release | fixed | https://en.wikipedia.org/wiki/Dani_Alves | fixed: kind=free |
| 98 | PSG contract expired June 2019, Sao Paulo signed him free | fixed | https://en.wikipedia.org/wiki/Dani_Alves | fixed: kind=free |
| 99 | Sao Paulo->Barcelona signed November 2021 as a free agent | fixed | https://getfootballnewsspain.com/official-barcelona-sign-dani-alves-on-free-transfer/ | fixed: kind=free,year=2021 |
| 182 | Napoli sold Cannavaro to Parma summer 1995, paid transfer | confirmed | https://en.wikipedia.org/wiki/Fabio_Cannavaro | none |
| 183 | Parma to Inter Milan 2002, paid transfer ~EUR 23m | confirmed | https://en.wikipedia.org/wiki/Fabio_Cannavaro | none |
| 184 | Inter Milan to Juventus 2004, part-exchange deal valued EUR 10m each | confirmed | https://en.wikipedia.org/wiki/Fabio_Cannavaro | none |
| 185 | Juventus to Real Madrid 2006, paid transfer EUR 7m | confirmed | https://en.wikipedia.org/wiki/Fabio_Cannavaro | none |
| 186 | Real Madrid contract expired June 2009; returned to Juventus on a free | fixed | https://en.wikipedia.org/wiki/Fabio_Cannavaro | fixed: kind=free |
| 224 | Independiente to Atletico Madrid 2006, paid transfer ~EUR 20m | confirmed | https://en.wikipedia.org/wiki/Sergio_Ag%C3%BCero | none |
| 225 | Atletico Madrid to Manchester City 2011, paid transfer GBP 35m | confirmed | https://en.wikipedia.org/wiki/Sergio_Ag%C3%BCero | none |
| 226 | Man City contract expired; Barcelona signed Aguero on a free, July 2021 | fixed | https://www.espn.com/soccer/story/_/id/37617756/barcelona-sign-sergio-aguero-manchester-city-free-transfer | fixed: kind=free |
| 344 | Anderlecht to Chelsea 2011, paid transfer ~EUR 12m | confirmed | https://en.wikipedia.org/wiki/Romelu_Lukaku | none |
| 345 | Chelsea to Everton 2014, permanent paid transfer GBP 28m (2013 loan is a separate move) | confirmed | https://en.wikipedia.org/wiki/Romelu_Lukaku | none |
| 346 | Everton to Manchester United 2017, paid transfer GBP 75m initial | confirmed | https://en.wikipedia.org/wiki/Romelu_Lukaku | none |
| 347 | Manchester United to Inter Milan 2019, paid transfer EUR 80m | confirmed | https://en.wikipedia.org/wiki/Romelu_Lukaku | none |
| 348 | Inter Milan to Chelsea 2021, paid transfer GBP 97.5m | confirmed | https://en.wikipedia.org/wiki/Romelu_Lukaku | none |
| 349 | Chelsea to Napoli 2024, permanent paid transfer ~EUR 30m | confirmed | https://www.espn.com/soccer/story/_/id/40966577/romelu-lukaku-joins-napoli-permanent-transfer-chelsea | none |
| 407 | Real Sociedad to Liverpool 2004, paid transfer GBP 10.7m | confirmed | https://en.wikipedia.org/wiki/Xabi_Alonso | none |
| 408 | Liverpool to Real Madrid 2009, paid transfer GBP 30m | confirmed | https://en.wikipedia.org/wiki/Xabi_Alonso | none |
| 409 | Real Madrid to Bayern Munich 2014, paid transfer, undisclosed fee | confirmed | https://en.wikipedia.org/wiki/Xabi_Alonso | none |
| 429 | Metz to Red Bull Salzburg 2012, paid transfer EUR 4m | confirmed | https://en.wikipedia.org/wiki/Sadio_Man%C3%A9 | none |
| 430 | Red Bull Salzburg to Southampton 2014, paid transfer GBP 11.8m | confirmed | https://en.wikipedia.org/wiki/Sadio_Man%C3%A9 | none |
| 431 | Southampton to Liverpool 2016, paid transfer GBP 34m | confirmed | https://en.wikipedia.org/wiki/Sadio_Man%C3%A9 | none |
| 432 | Liverpool to Bayern Munich 2022, paid transfer EUR 32m | confirmed | https://en.wikipedia.org/wiki/Sadio_Man%C3%A9 | none |
| 433 | Bayern Munich to Al-Nassr 2023, paid transfer, fee undisclosed | confirmed | https://en.wikipedia.org/wiki/Sadio_Man%C3%A9 | none |
| 528 | Paid transfer ~20M francs, Roma to AC Milan July 1987 | confirmed | https://en.wikipedia.org/wiki/Carlo_Ancelotti | none |
| 699 | Paid transfer ~EUR 30m, Inter Milan to Manchester City August 2010 | confirmed | https://www.mancity.com/news/first-team/first-team-news/archive/2010/august/mario-balotelli-signs-for-manchester-city | none |
| 700 | Paid transfer GBP 18m, Manchester City to AC Milan January 2013 | confirmed | https://www.mancity.com/news/first-team/first-team-news/2013/january/mario-balotelli-leaves-manchester-city | none |
| 701 | Paid transfer GBP 16m, AC Milan to Liverpool August 2014 | confirmed | https://feeds.bbci.co.uk/sport/0/football/28927162 | none |
| 702 | Left Liverpool on a free to Nice, August 2016 | fixed | https://feeds.bbci.co.uk/sport/football/37190434 | fixed: kind=free |
| 703 | Released by Nice, joined Marseille on a free, January 2019 | fixed | https://www.goal.com/en-in/news/balotelli-completes-move-to-marseille-after-nice-release/h9xjqd8l2a5u147s2nftzenac | fixed: kind=free |
| 706 | Paid transfer ~EUR 25m, Bayern Munich to Real Madrid July 2014 | confirmed | https://feeds.bbci.co.uk/sport/football/28342137 | none |
| 711 | Loan Real Madrid to Mallorca during the 1999-2000 season | confirmed | https://en.wikipedia.org/wiki/Samuel_Eto%27o | none |
| 712 | Paid transfer ~EUR 24m, Mallorca to Barcelona summer 2004 | confirmed | https://en.wikipedia.org/wiki/Samuel_Eto%27o | none |
| 713 | Swap deal with Ibrahimovic, Barcelona to Inter July 2009, a transfer | confirmed | https://www.france24.com/en/20090727-ibrahimovic-etoo-complete-inter-barca-swap- | none |
| 716 | Chelsea contract expired; joined Everton on a free, August 2014 | fixed | https://feeds.bbci.co.uk/sport/football/28930641 | fixed: kind=free |
| 910 | Paid transfer ~EUR 45m, Porto to Monaco 2013 | confirmed | https://feeds.bbci.co.uk/sport/0/football/22652097 | none |
| 911 | Paid transfer ~EUR 80m, Monaco to Real Madrid July 2014 | confirmed | https://feeds.bbci.co.uk/sport/football/28418131 | none |
| 912 | Paid transfer ~EUR 25m, Real Madrid to Everton September 2020 | confirmed | https://www.skysports.com/football/news/11671/12060691/james-rodriguez-joins-everton-from-real-madrid | none |
| 915 | Released by Olympiacos, joined Sao Paulo on a free, 2023 | fixed | https://www.flashscore.com/news/soccer-serie-a-former-real-madrid-forward-james-rodriguez-joins-sao-paulo-on-free-transfer/IBULGopQ | fixed: kind=free |
| 916 | Terminated Sao Paulo contract, joined Rayo Vallecano free, August 2024 | fixed | https://africa.espn.com/football/story/_/id/41000801/rayo-vallecano-confirm-transfer-colombia-james-rodriguez | fixed: kind=free |
| 1163 | GBP 6.9m paid to Independiente, signed January 2002 | confirmed | https://en.wikipedia.org/wiki/Diego_Forl%C3%A1n | none |
| 1164 | Undisclosed fee paid by Villarreal, permanent move August 2004 | confirmed | https://en.wikipedia.org/wiki/Diego_Forl%C3%A1n | none |
| 1165 | ~EUR 21m fee paid, June 2007 | confirmed | https://en.wikipedia.org/wiki/Diego_Forl%C3%A1n | none |
| 1166 | Permanent paid transfer, August 2011 | confirmed | https://en.wikipedia.org/wiki/Diego_Forl%C3%A1n | none |
| 1167 | Contract terminated by Inter July 2012, signed Internacional free | fixed | https://en.wikipedia.org/wiki/Diego_Forl%C3%A1n | fixed: kind=free |
| 1256 | Fee paid (GBP 1.2-1.5m reported), permanent transfer 1987 | confirmed | https://www.transfermarkt.co.uk/marco-van-basten/transfers/spieler/74471/transfer_id/192728 | none |
| 1281 | EUR 27m paid by Real Madrid, 2005 | confirmed | https://en.wikipedia.org/wiki/Sergio_Ramos | none |
| 1282 | Left Real Madrid on contract expiry June 2021, no fee | fixed | https://en.wikipedia.org/wiki/Sergio_Ramos | fixed: kind=free |
| 1283 | PSG contract expired 30 June 2023, returned to Sevilla free | fixed | https://en.wikipedia.org/wiki/Sergio_Ramos | fixed: kind=free |
| 1288 | Paid transfer to Dortmund, May 2016 | confirmed | https://en.wikipedia.org/wiki/Ousmane_Demb%C3%A9l%C3%A9 | none |
| 1289 | EUR 105m plus add-ons paid by Barcelona, 2017 | confirmed | https://en.wikipedia.org/wiki/Ousmane_Demb%C3%A9l%C3%A9 | none |
| 1290 | EUR 50.4m fee paid by PSG, August 2023 | confirmed | https://en.wikipedia.org/wiki/Ousmane_Demb%C3%A9l%C3%A9 | none |
| 1353 | EUR 8.5m paid by Lyon to Nice, 2008 | confirmed | https://en.wikipedia.org/wiki/Hugo_Lloris | none |
| 1354 | EUR 10m + 5m variable paid by Tottenham, 2012 | confirmed | https://en.wikipedia.org/wiki/Hugo_Lloris | none |
| 1364 | ~GBP 9.6m paid by Arsenal to Montpellier, 2012 | confirmed | https://en.wikipedia.org/wiki/Olivier_Giroud | none |
| 1365 | ~GBP 18m reported paid by Chelsea to Arsenal, 2018 | confirmed | https://en.wikipedia.org/wiki/Olivier_Giroud | none |
| 1375 | Left Real Madrid on contract expiry, signed Schalke free, July 2010 | fixed | https://www.espn.com/sports/soccer/news/_/id/5416429/ex-real-madrid-raul-gonzalez-joins-schalke-bundesliga | fixed: kind=free |
| 1590 | Arsenal paid a fee (~GBP 700k), youth transfer 2003 | confirmed | https://www.uefa.com/uefachampionsleague/news/0252-0cdca4b1b8e8-669524931f19-1000--arsenal-sign-spanish-youth-sensation/ | none |
| 1591 | Barcelona paid EUR 29m to Arsenal, August 2011 | confirmed | https://www.espn.com/sports/soccer/news/_/id/6864587/cesc-fabregas-seals-move-arsenal-barcelona | none |
| 1592 | Chelsea paid ~GBP 26.6m to Barcelona, June 2014 | confirmed | https://www.chinadaily.com.cn/sports/2014-06/13/content_17586315.htm | none |
| 1593 | Permanent deal, ~GBP 10m fee, January 2019 | confirmed | https://www.si.com/soccer/2019/01/11/chelsea-midfielder-cesc-fabregas-officially-joins-monaco-reported-ps10m-fee | none |
| 1645 | Chelsea club-record GBP 24m paid to Marseille, 2004 | confirmed | https://en.wikipedia.org/wiki/Didier_Drogba | none |
| 1648 | Chelsea confirmed Drogba returned on a free, July 2014 | fixed | https://en.wikipedia.org/wiki/Didier_Drogba | fixed: kind=free |
| 1650 | Barcelona contract expired, joined Inter Miami free, July 2023 | fixed | https://en.wikipedia.org/wiki/Sergio_Busquets | fixed: kind=free |
| 1706 | USD 25m paid by AC Milan to Dynamo Kyiv, 1999 | confirmed | https://en.wikipedia.org/wiki/Andriy_Shevchenko | none |
| 1707 | GBP 30.8m paid by Chelsea to AC Milan, May 2006 | confirmed | https://en.wikipedia.org/wiki/Andriy_Shevchenko | none |
| 1708 | Joined Dynamo Kyiv on a free, August 2009 | fixed | https://www.cbc.ca/sports/soccer/shevchenko-rejoins-ukraine-s-dynamo-kiev-1.818115 | fixed: kind=free |
| 1723 | ~EUR 17m paid by AC Milan to Inter, June 2001 | confirmed | https://en.wikipedia.org/wiki/Andrea_Pirlo | none |
| 1724 | Juventus signed Pirlo free after Milan contract expired, 2011 | fixed | https://en.wikipedia.org/wiki/Andrea_Pirlo | fixed: kind=free |
| 1790 | GBP 800k paid by Everton to Leicester City, 1985 | confirmed | https://en.wikipedia.org/wiki/Gary_Lineker | none |
| 1791 | GBP 2.8m paid by Barcelona to Everton, 1986 | confirmed | https://en.wikipedia.org/wiki/Gary_Lineker | none |
| 1792 | GBP 1.1m paid by Tottenham to Barcelona, July 1989 | confirmed | https://en.wikipedia.org/wiki/Gary_Lineker | none |
