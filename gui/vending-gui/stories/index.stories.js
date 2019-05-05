import React from 'react';

import {storiesOf} from '@storybook/react';
import VmState from "../src/VmState";
import Money from "../src/Money";
import Panel from "../src/Panel";
import InteractionPanel from "../src/InteractionPanel";
import LcdDisplay from "../src/LcdDisplay";



let vmState = {
    credit: 112,
    income: 0,
    quantity: [
        {
            code: '1',
            price: 5,
            quantity: 213,
            symbol: '🍬'
        },
        {
            code: '3',
            price: 15,
            quantity: 3,
            symbol: '🍕'
        },
        {
            code: '2',
            price: 25,
            quantity: 44,
            symbol: '🍺'
        }
    ],
    reportedExpiryDate: [],
    reportedShortage: []
};

storiesOf('Components', module).add("State", () => <VmState vmState={vmState}/>);
storiesOf('Components', module).add("Interaction panel", () => <InteractionPanel type="sm"/>);
storiesOf('Components', module).add("Money", () => <Money type="sm"/>);
storiesOf('Components', module).add("Panel", () => <Panel type="sm"/>);
storiesOf('Components', module).add("Lcd Display", () => <LcdDisplay message="Take your product and 5 of change"/>);
