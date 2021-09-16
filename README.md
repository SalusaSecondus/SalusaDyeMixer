# SalusaDyeMixer

To start and get it out of the way, **I am not a tie-dyer and don't know anything about it.**

One of my friends does tie-dye and recently complained to me that there was this [great tool for figuring out how to mix dyes for certain colors](http://yehar.com/blog/?p=307)
but thanks to [bit-rot](https://en.wikipedia.org/wiki/Software_rot) (and specifically the deprecation of [Java applets](https://en.wikipedia.org/wiki/Java_applet)), the tool no longer worked.
Fortunately, I know a *bit* of Java programming and offered to modify this tool enough that it can be run outside of a browser and not as an applet.
This way dyers can still use it without needing to worry about Java support in the browser (or the related security concerns of it).

## How to download and use

There are a few ways to do this depending on how your computer is setup and how you want to manage things.
In all cases you start by going to the [latest release](https://github.com/SalusaSecondus/SalusaDyeMixer/releases/latest) page of this repo.

### You already have Java 11 (or newer) installed (universal)

Even if you aren't sure, this is the one you should try first.

1. Download the `SalusaDyeMixer.jar` file
2. Double-click it

Did it work? If so, great!

If it didn't, you could just choose to [install Java 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/what-is-corretto-11.html) on your computer.
(Instructions for each operating system are on the left.)
Personally, I like this method, but if you aren't comfortable with it, I understand.

### Standalone version

I have also created an installable stand-alone version of this program which bundles a copy of Java with it.
This should "just work" but I can't fully describe the installation procedure as I don't have easy access to all of computers

1. Download your system's correct installation file
   * Windows: Download the `.exe`
   * Mac: Download the `.dmg` **(This appears to be broken right now and I need assistance from a Java dev to fix it.)**
   * Linux: I only have a `.deb` available for now, but if you are running Linux, you can figure this out yourself.
2. Follow the instructions on the screen
3. Run the program from whereever it is installed:
   * Windows: It will normally install in `c:\Program Files\SalusaDyeMixer`

### You already have Java 11 (or newer) installed (platform specific)

*I'm including this method for completeness but think it is probably not the right one for most people.*

You might have Java installed but your computer doesn't know where to find it.
This is relatively unlikely, so this download method is probably worth skipping.

1. Download `SalusaDyeMixer.zip` and extract it somewhere
2. If you are on Windows, double click on `SalusaDyeMixer.bat` in the `bin` sub-directory of what you extracted. (e.g., `SalusaDyeMixer/bin/SalusaDyeMixer.bat`)
3. If you are on Mac or Linux, double click on the file just called `SalusaDyeMixer` in the sub-directory of what you extracted. (e.g., `SalusaDyeMixer/bin/SalusaDyeMixer`)

Did it work? If so, great!

## Questions/Features/Etc.

I am not a dyer and not the original author.
I've made the minimal changes needed for this to run and am really uncertain that I can make any substantial changes to the code to fix things or add new features.
Feel free to [create an issue](https://github.com/SalusaSecondus/SalusaDyeMixer/issues) to ask me things, but I'm unlikely to be able to help. Sorry.

## Versioning

I'm adopting a three part version where the first (major) part is the version number for my modifications and the last two parts correspond to the [version I'm bundling](http://yehar.com/blog/?p=307#comment-1256021).
(I'm starting at 2 for "reasons".) So, my initial release is 2.1.1 meaning it is my second version and bundles the 1.1 version by Olli Niemitalo.

## A note about the licensing

My deep thanks to the original author [Olli Niemitalo](http://yehar.com/blog/) who [released his code into the public domain](http://yehar.com/blog/?p=307#comment-1256021)
under the [CC0 1.0 Universal (CC0 1.0) Public Domain Dedication](https://creativecommons.org/publicdomain/zero/1.0/).
My new version is *not* released to the public domain but is rather under the [Apache 2.0 License](LICENSE).
I've done this to provide better clarity to consumers about what they are allowed to do with the code and that they have explicit permission to do what they like with it.
It is still open-source and free to distribute/modify/consume however you see fit. But now the license is slightly more clear about how this works.
If you are not a lawyer or a corporation, you still don't need to worry about the licensing because you can just grab this and use it however you want.
If the Apache 2.0 license doesn't work for your for some reason, please just let me know and I'll happily work with you so that you can use this.
