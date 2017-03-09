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

            if (inputString.includes("(echo)")) {
                this.log(cli.chalk['blue'](inputString))
            } else if (inputString.includes("(all)")) {
                this.log(cli.chalk['yellow'](inputString))
            } else if (inputString.includes("(whisper)")) {
                this.log(cli.chalk['magenta'](inputString))
            } else if (inputString.includes("has connected")) {
                this.log(cli.chalk['cyan'](inputString))
            } else if (inputString.includes("has disconnected")) {
                this.log(cli.chalk['white'](inputString))
            } else if (inputString.includes("currently connected users:")) {
                this.log(cli.chalk['red'](inputString))
            }
        })

        server.on('end', () => {
            cli.exec('exit')
        })
    })
    .action(function (input, callback) {
        let [command, ...rest] = input.replace(/--|-/, "").split(" ")
        let contents = rest.join(' ')

        let commands = ['echo', 'broadcast', 'users']

        if (command === 'disconnect') {
            server.end(new Message({username, command}).toJSON() + '\n')
        } else if (command.startsWith("@")) {
            server.write(new Message({username, command, contents}).toJSON() + '\n')
            lastCommand = command
            this.log(lastCommand)
        } else if (commands.indexOf(command) > -1) {
            server.write(new Message({username, command, contents}).toJSON() + '\n')
            this.log(command)
            lastCommand = command
        } else {
            if (lastCommand === null) {
                this.log(`Command <${command}> was not recognized`)
            } else {
                contents = command
                command = lastCommand
                server.write(new Message({username, command, contents}).toJSON() + '\n')
            }
        }

        callback()
    })

cli
    .delimiter(cli.chalk['yellow']('ftd~$'))
