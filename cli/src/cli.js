import vorpal from 'vorpal'
import {words} from 'lodash'
import {connect} from 'net'
import {Message} from './Message'

export const cli = vorpal()

let username
let server
let host
let port
var lastCommand = null;

cli
    .mode('connect <username> [host] [port]')
    .delimiter(cli.chalk['green']('connected>'))
    .init(function (args, callback) {
        username = args.username
        host = args.host != undefined ? args.host : 'localhost'
        port = args.port != undefined ? args.port : 8080
        server = connect({host: host, port: port}, () => {
            server.write(new Message({username, command: 'connect'}).toJSON() + '\n')
            callback()
        })

        server.on('data', (buffer) => {
            let inputString = Message.fromJSON(buffer).toString()

            let color

            if (inputString.includes("(echo)")) {
                color = 'blue'
            } else if (inputString.includes("(all)")) {
                color = 'yellow'
            } else if (inputString.includes("(whisper)")) {
                color = 'magenta'
            } else if (inputString.includes("has connected")) {
                color = 'cyan'
            } else if (inputString.includes("has disconnected")) {
                color = 'white'
            } else if (inputString.includes("currently connected users:")) {
                color = 'red'
            } else {
                color = 'green'
            }
            this.log(cli.chalk[color](inputString))
        })

        server.on('end', () => {
            cli.exec('exit')
        })
    })
    .action(function (input, callback) {
        let [command, ...rest] = input.split(" ")
        let contents = rest.join(' ')

        let commands = ['echo', 'broadcast', 'users']

        if (command === 'disconnect') {
            server.end(new Message({username, command}).toJSON() + '\n')
        } else if (command.startsWith("@") || commands.indexOf(command) > -1) {
            server.write(new Message({username, command, contents}).toJSON() + '\n')
            lastCommand = command
        } else {
            if (lastCommand === null) {
                this.log(`Command <${command}> was not recognized`)
            } else {
                contents = command +  " " + contents
                command = lastCommand
                server.write(new Message({username, command, contents}).toJSON() + '\n')
            }
        }
        callback()
    })

cli
    .delimiter(cli.chalk['yellow']('ftd~$'))
