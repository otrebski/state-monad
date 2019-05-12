import React, {Component} from 'react';
import "./Money.css"

class Money extends Component {

    insertCoin = amount => {
        let url = "/api/" + this.props.type + "/0/credit/" + amount;
        fetch(url)
    };

    createInsertPanel = () => {
        return <div>
            <button className="coin" onClick={() => this.insertCoin(1)}>
                <span role="img" aria-label="Monyebag">&#x1F4B0;</span>
            </button>
            <br/>
            <button className="coin" onClick={() => this.insertCoin(2)}>
                <span role="img" aria-label="Monyebag">&#x1F4B0; &#x1F4B0;</span>
            </button>
            <br/>
            <button className="coin" onClick={() => this.insertCoin(5)}>
                <span role="img" aria-label="Monyebag">&#x1F4B0; &#x1F4B0; &#x1F4B0; &#x1F4B0; &#x1F4B0;</span>
            </button>
            <br/>
        </div>
    };


    render() {
        return <div>{this.createInsertPanel()}</div>
    }

}

export default Money